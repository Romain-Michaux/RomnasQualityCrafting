package dev.hytalemodding.quality;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Crée une variante qualité d’un item de base : même type (weapon/tool/armor),
 * id = baseId_Quality, durabilité et dégâts/puissance scaled par les multiplicateurs de qualité.
 * Utilise la réflexion pour ne pas dépendre des constructeurs internes d’Item.
 */
public final class DynamicQualityVariantFactory {

    @Nullable
    public static Item createVariant(@Nullable Item base, @Nullable String baseId, @Nullable ItemQuality quality) {
        if (base == null || baseId == null || base == Item.UNKNOWN || quality == null) {
            return null;
        }

        float durabilityMul = quality.getDurabilityMultiplier();
        float damageMul = quality.getDamageMultiplier();
        String qualityId = baseId + "_" + quality.getDisplayName();

        Item candidate = cloneViaCodec(base);
        if (candidate == null) {
            candidate = cloneViaCopyConstructor(base);
        }
        if (candidate == null) {
            candidate = cloneViaCloneable(base);
        }
        if (candidate == null) {
            candidate = cloneViaNewInstanceAndFieldCopy(base);
        }

        if (candidate == null) {
            return null;
        }

        setFieldOrInvoke(candidate, "id", qualityId, String.class);
        setFieldOrInvoke(candidate, "Id", qualityId, String.class);
        setFieldOrInvoke(candidate, "quality", quality.getDisplayName(), String.class);

        double baseMax = getMaxDurability(base);
        if (baseMax > 0) {
            double newMax = Math.max(1, baseMax * durabilityMul);
            setFieldOrInvoke(candidate, "maxDurability", (double) newMax, Double.class);
            setFieldOrInvoke(candidate, "MaxDurability", (double) newMax, Double.class);
        }

        scaleToolPower(candidate, damageMul);
        scaleWeaponDamage(candidate, damageMul);
        scaleArmorStats(candidate, damageMul);

        return candidate;
    }

    @Nullable
    private static Item cloneViaCodec(Item base) {
        try {
            Field codecField = null;
            for (Field f : Item.class.getDeclaredFields()) {
                String n = f.getName().toUpperCase();
                if ((n.equals("CODEC") || n.equals("ABSTRACT_CODEC")) && f.getType().getName().contains("Codec")) {
                    codecField = f;
                    break;
                }
            }
            if (codecField == null) return null;
            codecField.setAccessible(true);
            Object codec = codecField.get(null);
            if (codec == null) return null;
            Method encode = codec.getClass().getMethod("encode", Object.class);
            Object encoded = encode.invoke(codec, base);
            if (encoded == null) return null;
            Method decode = codec.getClass().getMethod("decode", encoded.getClass());
            Object decoded = decode.invoke(codec, encoded);
            return decoded instanceof Item ? (Item) decoded : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Item cloneViaCopyConstructor(Item base) {
        try {
            for (Constructor<?> c : Item.class.getDeclaredConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == Item.class) {
                    c.setAccessible(true);
                    return (Item) c.newInstance(base);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Item cloneViaCloneable(Item base) {
        try {
            if (base instanceof Cloneable) {
                Method clone = base.getClass().getDeclaredMethod("clone");
                clone.setAccessible(true);
                Object o = clone.invoke(base);
                return o instanceof Item ? (Item) o : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Item cloneViaNewInstanceAndFieldCopy(Item base) {
        try {
            Constructor<?> noArg = Item.class.getDeclaredConstructor();
            noArg.setAccessible(true);
            Item copy = (Item) noArg.newInstance();
            for (Field f : Item.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                try {
                    f.set(copy, f.get(base));
                } catch (Exception ignored) {
                }
            }
            return copy;
        } catch (Exception e) {
            return null;
        }
    }

    private static double getMaxDurability(Item item) {
        try {
            Method m = Item.class.getMethod("getMaxDurability");
            Object v = m.invoke(item);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static void setFieldOrInvoke(Object target, String name, Object value, Class<?> valueClass) {
        Class<?> c = target.getClass();
        try {
            Method setter = findSetter(c, name, valueClass);
            if (setter != null) {
                setter.invoke(target, value);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase(name) && (valueClass == null || valueClass.isInstance(value))) {
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static Method findSetter(Class<?> c, String propertyName, Class<?> argType) {
        String setterName = "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        for (Method m : c.getMethods()) {
            if (m.getName().equals(setterName) && m.getParameterCount() == 1
                && (argType == null || m.getParameterTypes()[0].isAssignableFrom(argType))) {
                return m;
            }
        }
        return null;
    }

    private static void scaleToolPower(Item item, float multiplier) {
        try {
            Object tool = item.getTool();
            if (tool == null) return;
            Object specList = invokeGetter(tool, "getSpecs");
            if (specList == null) specList = invokeGetter(tool, "specs");
            if (specList instanceof Iterable) {
                for (Object spec : (Iterable<?>) specList) {
                    if (spec != null) scalePowerInSpec(spec, multiplier);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void scalePowerInSpec(Object spec, float multiplier) {
        try {
            Method getPower = spec.getClass().getMethod("getPower");
            Object p = getPower.invoke(spec);
            if (p instanceof Number) {
                double newVal = ((Number) p).doubleValue() * multiplier;
                Method setPower = spec.getClass().getMethod("setPower", double.class);
                setPower.invoke(spec, newVal);
            }
        } catch (Exception ignored) {
        }
    }

    private static void scaleArmorStats(Item item, float multiplier) {
        try {
            Object armor = item.getArmor();
            if (armor == null) return;

            // 1. Armor.BaseDamageResistance
            try {
                Field f = armor.getClass().getDeclaredField("baseDamageResistance");
                f.setAccessible(true);
                Object val = f.get(armor);
                if (val instanceof Number) {
                    double newVal = ((Number) val).doubleValue() * multiplier;
                    f.set(armor, newVal);
                } else if (val != null) {
                    Method setter = findSetter(armor.getClass(), "baseDamageResistance", double.class);
                    if (setter != null) setter.invoke(armor, ((Number) val).doubleValue() * multiplier);
                }
            } catch (Exception ignored) {}

            // 2. Armor.DamageResistance.*[].Amount
            try {
                Field f = armor.getClass().getDeclaredField("damageResistance");
                f.setAccessible(true);
                Object resList = f.get(armor);
                if (resList instanceof Iterable) {
                    for (Object entry : (Iterable<?>) resList) {
                        if (entry != null) {
                            try {
                                Field amountF = entry.getClass().getDeclaredField("amount");
                                amountF.setAccessible(true);
                                Object val = amountF.get(entry);
                                if (val instanceof Number) {
                                    double newVal = ((Number) val).doubleValue() * multiplier;
                                    amountF.set(entry, newVal);
                                } else if (val != null) {
                                    Method setter = findSetter(entry.getClass(), "amount", double.class);
                                    if (setter != null) setter.invoke(entry, ((Number) val).doubleValue() * multiplier);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 3. Armor.StatModifiers.*[].Amount
            try {
                Field f = armor.getClass().getDeclaredField("statModifiers");
                f.setAccessible(true);
                Object modList = f.get(armor);
                if (modList instanceof Iterable) {
                    for (Object entry : (Iterable<?>) modList) {
                        if (entry != null) {
                            try {
                                Field amountF = entry.getClass().getDeclaredField("amount");
                                amountF.setAccessible(true);
                                Object val = amountF.get(entry);
                                if (val instanceof Number) {
                                    double newVal = ((Number) val).doubleValue() * multiplier;
                                    amountF.set(entry, newVal);
                                } else if (val != null) {
                                    Method setter = findSetter(entry.getClass(), "amount", double.class);
                                    if (setter != null) setter.invoke(entry, ((Number) val).doubleValue() * multiplier);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 4. Armor.DamageClassEnhancement.*[].Amount
            try {
                Field f = armor.getClass().getDeclaredField("damageClassEnhancement");
                f.setAccessible(true);
                Object enhList = f.get(armor);
                if (enhList instanceof Iterable) {
                    for (Object entry : (Iterable<?>) enhList) {
                        if (entry != null) {
                            try {
                                Field amountF = entry.getClass().getDeclaredField("amount");
                                amountF.setAccessible(true);
                                Object val = amountF.get(entry);
                                if (val instanceof Number) {
                                    double newVal = ((Number) val).doubleValue() * multiplier;
                                    amountF.set(entry, newVal);
                                } else if (val != null) {
                                    Method setter = findSetter(entry.getClass(), "amount", double.class);
                                    if (setter != null) setter.invoke(entry, ((Number) val).doubleValue() * multiplier);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception ignored) {
        }
    }

    private static void scaleWeaponDamage(Item item, float multiplier) {
        try {
            Object weapon = item.getWeapon();
            if (weapon == null) return;
            Object interactionVars = invokeGetter(weapon, "getInteractionVars");
            if (interactionVars == null) interactionVars = invokeGetter(item, "getInteractionVars");
            if (interactionVars instanceof java.util.Map) {
                for (Object entry : ((java.util.Map<?, ?>) interactionVars).values()) {
                    scaleDamageInInteractionVar(entry, multiplier);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void scaleDamageInInteractionVar(Object var, float multiplier) {
        if (var == null) return;
        try {
            Object interactions = invokeGetter(var, "getInteractions");
            if (interactions instanceof Iterable) {
                for (Object ia : (Iterable<?>) interactions) {
                    if (ia != null) {
                        Object calc = invokeGetter(ia, "getDamageCalculator");
                        if (calc != null) {
                            Object baseDamage = invokeGetter(calc, "getBaseDamage");
                            if (baseDamage instanceof java.util.Map) {
                                java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) baseDamage;
                                for (Object key : new java.util.ArrayList<>(map.keySet())) {
                                    Object val = map.get(key);
                                    if (val instanceof Number) {
                                        double newVal = ((Number) val).doubleValue() * multiplier;
                                        map.put(key, newVal);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static Object invokeGetter(Object o, String methodName) {
        try {
            Method m = o.getClass().getMethod(methodName);
            return m.invoke(o);
        } catch (Exception e) {
            return null;
        }
    }
}
