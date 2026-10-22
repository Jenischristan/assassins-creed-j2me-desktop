import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Null-guards the game's unconditional dereferences of touch-feedback sprite
 * e.e[13] inside the obfuscated game class e.
 *
 * The sprite at index 13 of the static array <code>e.e</code> (type c[]) is
 * only loaded by the progressive resource loader e.L(int) when the level pack
 * declares touch data (dF[13] == 1). On builds whose pack does not declare it,
 * e.e[13] stays null forever - the game's own paint code even null-checks it
 * ("cS != -1 && e[13] != null && x < 30"). But four sites still dereference it
 * without a check:
 *
 *   e.z()               x3 :  e.e[13].s = 1   (touch-button press feedback)
 *   e.pointerReleased() x1 :  e.e[13].s = 0   (touch-button release feedback)
 *
 * On the desktop port every mouse click therefore threw a NullPointerException
 * at the very end of pointerReleased() (after the useful work was already
 * done). This patcher rewrites each of the four
 *
 *   getstatic e.e : [Lc;  /  bipush 13  /  aaload  /  iconst_0|1  /  putfield c.s : I
 *
 * sequences into the equivalent of
 *
 *   c tmp = e.e[13];
 *   if (tmp != null) { tmp.s = <0|1>; }
 *
 * by inserting dup/ifnonnull/pop/goto around the original write. The rest of
 * the bytecode is untouched; stack maximums are recomputed by ASM.
 *
 * Usage: java PatchE <input e.class> <output e.class>
 * Exits non-zero unless exactly the expected number of sites was patched, so
 * a changed game jar can never silently ship an incomplete patch.
 */
public final class PatchE implements Opcodes {

    // site count: 3 in z() (s = 1) + 1 in pointerReleased() (s = 0)
    static final int EXPECTED_SITES = 4;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: PatchE <in e.class> <out e.class>");
            System.exit(2);
        }
        byte[] in = Files.readAllBytes(Paths.get(args[0]));

        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        int[] patched = new int[1];

        cr.accept(new ClassVisitor(ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                return new SiteGuard(super.visitMethod(access, name, desc, signature, exceptions),
                                     name, patched);
            }
        }, 0);

        byte[] out = cw.toByteArray();
        Files.write(Paths.get(args[1]), out);

        int n = patched[0];
        System.out.println("[PatchE] null-guarded " + n + "/" + EXPECTED_SITES
                + " e.e[13].s write(s) in e.class");
        if (n != EXPECTED_SITES) {
            throw new IllegalStateException("PatchE: expected " + EXPECTED_SITES
                    + " sites, patched " + n + " - game jar changed? Refuse to ship partial patch.");
        }
    }

    /**
     * Streams through one method, recognising the 5-instruction pattern
     * getstatic e.e:[Lc; / bipush 13 / aaload / iconst_0|1 / putfield c.s:I
     * and re-emitting it with a null guard around the putfield.
     */
    private static final class SiteGuard extends MethodVisitor {
        private final String methodName;
        private final int[] patched;

        /** 0 = outside pattern, 1 = saw getstatic e.e, 2 = saw bipush 13,
         *  3 = saw aaload (waiting for the guarded putfield). */
        private int state;
        private int pendingIconst = -1; // ICONST_0 or ICONST_1 deferred while state == 3
        private Label writeLabel;       // target of the ifnonnull (the guarded write)
        private Label endLabel;         // merge point after the guarded write

        SiteGuard(MethodVisitor mv, String methodName, int[] patched) {
            super(ASM9, mv);
            this.methodName = methodName;
            this.patched = patched;
        }

        private void abortSite() {
            // Forward any deferred value then forget the pattern. Nothing has
            // been emitted out of order, so the stream stays consistent.
            flushPending();
            state = 0;
            pendingIconst = -1;
            writeLabel = null;
            endLabel = null;
        }

        private void flushPending() {
            if (pendingIconst >= 0) {
                super.visitInsn(pendingIconst);
                pendingIconst = -1;
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (state == 3 && opcode == PUTFIELD && "c".equals(owner)
                    && "s".equals(name) && "I".equals(desc)) {
                // Full pattern matched. Emit:
                //   dup; ifnonnull WRITE; pop; goto END; WRITE: <iconst>; putfield; END:
                super.visitInsn(DUP);
                super.visitJumpInsn(IFNONNULL, writeLabel);
                super.visitInsn(POP);
                super.visitJumpInsn(GOTO, endLabel);
                super.visitLabel(writeLabel);
                super.visitInsn(pendingIconst);        // ICONST_0 or ICONST_1
                super.visitFieldInsn(opcode, owner, name, desc);
                super.visitLabel(endLabel);
                patched[0]++;
                state = 0;
                pendingIconst = -1;
                writeLabel = null;
                endLabel = null;
                return;
            }
            boolean startsPattern = opcode == GETSTATIC && "e".equals(owner)
                    && "e".equals(name) && "[Lc;".equals(desc);
            if (state == 3) {
                abortSite();               // different putfield while in pattern
                if (startsPattern) {
                    state = 1;             // ...and it starts a new pattern
                }
            } else {
                state = startsPattern ? 1 : 0;
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (state == 3) {
                abortSite();
            } else if (state == 1 && opcode == BIPUSH && operand == 13) {
                state = 2;
            } else {
                state = 0;
            }
            super.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == AALOAD && state == 2) {
                state = 3;
                writeLabel = new Label();
                endLabel = new Label();
            } else if (state == 3 && (opcode == ICONST_0 || opcode == ICONST_1)
                    && pendingIconst < 0) {
                pendingIconst = opcode;    // defer the stored value
                return;                    // do NOT forward it to the underlying writer
            } else if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                                    boolean isInterface) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitLdcInsn(value);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            if (state == 3) {
                abortSite();
            } else {
                state = 0;
            }
            super.visitIincInsn(varIndex, increment);
        }

        @Override
        public void visitLabel(Label label) {
            if (state == 3) {
                // A branch target between aaload and putfield would break the
                // guarded emission; give up on this site.
                abortSite();
            }
            super.visitLabel(label);
        }
    }
}
