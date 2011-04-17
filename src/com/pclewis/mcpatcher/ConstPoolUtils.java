package com.pclewis.mcpatcher;

import javassist.bytecode.ConstPool;
import javassist.bytecode.Mnemonic;

import java.util.ArrayList;

import static javassist.bytecode.Opcode.*;

class ConstPoolUtils {
    private static final byte[] NOT_FOUND = null;

    public static int getTag(Object o) {
        if (o instanceof Float) {
            return ConstPool.CONST_Float;
        } else if (o instanceof Double) {
            return ConstPool.CONST_Double;
        } else if (o instanceof Integer) {
            return ConstPool.CONST_Integer;
        } else if (o instanceof Long) {
            return ConstPool.CONST_Long;
        } else if (o instanceof String) {
            return ConstPool.CONST_String;
        } else if (o instanceof MethodRef) {
            return ConstPool.CONST_Methodref;
        } else if (o instanceof InterfaceMethodRef) {
            return ConstPool.CONST_InterfaceMethodref;
        } else if (o instanceof FieldRef) {
            return ConstPool.CONST_Fieldref;
        } else if (o instanceof ClassRef) {
            return ConstPool.CONST_Class;
        }
        throw new IllegalArgumentException("Unhandled type: " + o.getClass().getName());
    }

    private static int addToPool(ConstPool cp, Object o) {
        if (o instanceof Float) {
            return cp.addFloatInfo((Float) o);
        } else if (o instanceof Double) {
            return cp.addDoubleInfo((Double) o);
        } else if (o instanceof Integer) {
            return cp.addIntegerInfo((Integer) o);
        } else if (o instanceof Long) {
            return cp.addLongInfo((Long) o);
        } else if (o instanceof String) {
            return cp.addStringInfo((String) o);
        } else if (o instanceof MethodRef) {
            MethodRef mr = (MethodRef) o;
            int ci = findOrAdd(cp, new ClassRef(mr.getClassName().replaceAll("\\.", "/")));
            return cp.addMethodrefInfo(ci, mr.getName(), mr.getType());
        } else if (o instanceof InterfaceMethodRef) {
            InterfaceMethodRef imr = (InterfaceMethodRef) o;
            int ci = findOrAdd(cp, new ClassRef(imr.getClassName().replaceAll("\\.", "/")));
            return cp.addInterfaceMethodrefInfo(ci, imr.getName(), imr.getType());
        } else if (o instanceof FieldRef) {
            FieldRef fr = (FieldRef) o;
            int ci = findOrAdd(cp, new ClassRef(fr.getClassName().replaceAll("\\.", "/")));
            return cp.addFieldrefInfo(ci, fr.getName(), fr.getType());
        } else if (o instanceof ClassRef) {
            return cp.addClassInfo(((ClassRef) o).getClassName());
        }
        throw new IllegalArgumentException("Unhandled type: " + o.getClass().getName());
    }

    public static boolean checkEqual(ConstPool cp, int index, Object o) {
        if (o instanceof Float) {
            return cp.getFloatInfo(index) == (Float) o;
        } else if (o instanceof Double) {
            return cp.getDoubleInfo(index) == (Double) o;
        } else if (o instanceof Integer) {
            return cp.getIntegerInfo(index) == (Integer) o;
        } else if (o instanceof Long) {
            return cp.getLongInfo(index) == (Long) o;
        } else if (o instanceof String) {
            return o.equals(cp.getStringInfo(index));
        } else if (o instanceof JavaRef) {
            return ((JavaRef) o).checkEqual(cp, index);
        }
        throw new IllegalArgumentException("Unhandled type: " + o.getClass().getName());
    }

    private static int find(ConstPool cp, Object value) {
        int index = -1;
        int tag = getTag(value);
        for (int i = 1; i < cp.getSize(); ++i) {
            if (cp.getTag(i) == tag) {
                if (checkEqual(cp, i, value)) {
                    index = i;
                    break;
                }
            }
        }
        return index;
    }

    private static int findOrAdd(ConstPool cp, Object value) {
        int index = find(cp, value);
        if (index == -1) {
            index = addToPool(cp, value);
        }
        return index;
    }

    private static byte[] getLoad(int op, int i) {
        int mop = op;
        if (i > Byte.MAX_VALUE && mop == LDC) {
            mop = LDC_W;
        }
        if (mop == LDC) {
            return new byte[]{(byte) mop, Util.b(i, 0)};
        } else {
            return new byte[]{(byte) mop, Util.b(i, 1), Util.b(i, 0)};
        }
    }

    private static Object getLoadExpr(int op, int i) {
        int mop = op;
        if (i > Byte.MAX_VALUE && mop == LDC) {
            mop = LDC_W;
        }
        if (mop == LDC) {
            // obfuscator occasionally uses LDC_W even when LDC would do, so we're forced to generate a regex here
            return BinaryRegex.or(
                BinaryRegex.build(new byte[]{LDC, Util.b(i, 0)}),
                BinaryRegex.build(new byte[]{LDC_W, Util.b(i, 1), Util.b(i, 0)})
            );
        } else {
            return new byte[]{(byte) mop, Util.b(i, 1), Util.b(i, 0)};
        }
    }

    public static Object push(ConstPool cp, Object value, boolean add) {
        if (value instanceof Integer) {
            int i = (Integer) value;
            switch (i) {
                case -1:
                    return new byte[]{ICONST_M1};
                case 0:
                    return new byte[]{ICONST_0};
                case 1:
                    return new byte[]{ICONST_1};
                case 2:
                    return new byte[]{ICONST_2};
                case 3:
                    return new byte[]{ICONST_3};
                case 4:
                    return new byte[]{ICONST_4};
                case 5:
                    return new byte[]{ICONST_5};
                default:
                    break;
            }
            if (i <= Byte.MAX_VALUE) {
                return new byte[]{BIPUSH, (byte) i};
            } else if (i <= Short.MAX_VALUE) {
                return new byte[]{SIPUSH, Util.b(i, 1), Util.b(i, 0)};
            }
        } else if (value instanceof Long) {
            long l = (Long) value;
            if (l == 0L) {
                return new byte[]{LCONST_0};
            } else if (l == 1L) {
                return new byte[]{LCONST_1};
            }
        } else if (value instanceof Float) {
            float f = (Float) value;
            if (f == 0.0F) {
                return new byte[]{FCONST_0};
            } else if (f == 1.0F) {
                return new byte[]{FCONST_1};
            } else if (f == 2.0F) {
                return new byte[]{FCONST_2};
            }
        } else if (value instanceof Double) {
            double d = (Double) value;
            if (d == 0.0) {
                return new byte[]{DCONST_0};
            } else if (d == 1.0) {
                return new byte[]{DCONST_1};
            }
        }
        int index = add ? findOrAdd(cp, value) : find(cp, value);
        if (index >= 0) {
            int op = (value instanceof Double || value instanceof Long ? LDC2_W : LDC);
            if (add) {
                return getLoad(op, index);
            } else {
                return getLoadExpr(op, index);
            }
        } else {
            return NOT_FOUND;
        }
    }

    public static byte[] reference(ConstPool cp, Object value, boolean add) {
        int index = add ? findOrAdd(cp, value) : find(cp, value);
        if (index < 0) {
            return NOT_FOUND;
        }
        return Util.marshal16(index);
    }

    public static byte[] reference(ConstPool cp, int opcode, Object value, boolean add) {
        int index = add ? findOrAdd(cp, value) : find(cp, value);
        if (index < 0) {
            return NOT_FOUND;
        }
        opcode &= 0xff;
        switch (opcode) {
            case LDC:
            case LDC_W:
            case LDC2_W:
                return getLoad(opcode, index);

            case GETFIELD:
            case GETSTATIC:
            case PUTFIELD:
            case PUTSTATIC:
                if (!(value instanceof FieldRef)) {
                    throw new IllegalArgumentException(Mnemonic.OPCODE[opcode] + " requires a FieldRef object");
                }
                break;

            case INVOKEVIRTUAL:
            case INVOKESTATIC:
            case INVOKESPECIAL:
                if (!(value instanceof MethodRef)) {
                    throw new IllegalArgumentException(Mnemonic.OPCODE[opcode] + " requires a MethodRef object");
                }
                break;

            case INVOKEINTERFACE:
                if (!(value instanceof InterfaceMethodRef)) {
                    throw new IllegalArgumentException(Mnemonic.OPCODE[opcode] + " requires an InterfaceMethodRef object");
                }
                int numArgs = parseDescriptor(((InterfaceMethodRef) value).getType()).size();
                return new byte[]{(byte) opcode, Util.b(index, 1), Util.b(index, 0), (byte) numArgs, 0};

            case INSTANCEOF:
            case CHECKCAST:
            case MULTIANEWARRAY:
            case ANEWARRAY:
            case NEW:
                if (!(value instanceof ClassRef)) {
                    throw new IllegalArgumentException(Mnemonic.OPCODE[opcode] + " requires a ClassRef object");
                }
                break;

            default:
                break;
        }
        return new byte[]{(byte) opcode, Util.b(index, 1), Util.b(index, 0)};
    }

    public static ArrayList<String> parseDescriptor(String desc) {
        ArrayList<String> types = new ArrayList<String>();
        desc = desc.replaceAll("[()]", "");
        int len = desc.length();
        int j;
        for (int i = 0; i < len; i = j + 1) {
            for (j = i; j < len; j++) {
                char c = desc.charAt(j);
                if ("SIFDZV".indexOf(c) >= 0) {
                    break;
                } else if (c == 'L') {
                    while (desc.charAt(j) != ';') {
                        j++;
                    }
                    break;
                }
            }
            types.add(desc.substring(i, j + 1));
        }
        return types;
    }
}
