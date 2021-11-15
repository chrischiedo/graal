/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;

public final class ExtensionFieldsMetadata {
    @CompilationFinal(dimensions = 1) private Field[] addedInstanceFields = Field.EMPTY_ARRAY;
    @CompilationFinal(dimensions = 1) private Field[] addedStaticFields = Field.EMPTY_ARRAY;

    public void addNewStaticFields(ObjectKlass.KlassVersion holder, List<ParserField> newFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields,
                    ClassRedefinition classRedefinition) {
        CompilerAsserts.neverPartOfCompilation();

        if (newFields.isEmpty()) {
            return;
        }
        List<Field> toAdd = initNewFields(holder, newFields, pool, compatibleFields, classRedefinition);
        int nextIndex = addedStaticFields.length;
        addedStaticFields = Arrays.copyOf(addedStaticFields, addedStaticFields.length + toAdd.size());
        for (Field field : toAdd) {
            addedStaticFields[nextIndex++] = field;
        }
    }

    public void addNewInstanceFields(ObjectKlass.KlassVersion holder, List<ParserField> newFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields,
                    ClassRedefinition classRedefinition) {
        CompilerAsserts.neverPartOfCompilation();

        if (newFields.isEmpty()) {
            return;
        }
        List<Field> toAdd = initNewFields(holder, newFields, pool, compatibleFields, classRedefinition);
        int nextIndex = addedInstanceFields.length;
        addedInstanceFields = Arrays.copyOf(addedInstanceFields, addedInstanceFields.length + toAdd.size());
        for (Field field : toAdd) {
            addedInstanceFields[nextIndex++] = field;
        }
    }

    private static List<Field> initNewFields(ObjectKlass.KlassVersion holder, List<ParserField> instanceFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields,
                    ClassRedefinition classRedefinition) {
        List<Field> toAdd = new ArrayList<>(instanceFields.size());
        for (ParserField newField : instanceFields) {
            int nextFieldSlot = classRedefinition.getNextAvailableFieldSlot();
            LinkedField linkedField = new LinkedField(newField, nextFieldSlot, LinkedField.IdMode.REDEFINE_ADDED);
            Field field = new RedefineAddedField(holder, linkedField, pool, false);
            toAdd.add(field);

            // mark a compatible field where
            // state could potentially be copied from
            // but only if the class has been initialized
            Field compatibleField = compatibleFields.get(newField);
            if (compatibleField != null) {
                if (compatibleField.getDeclaringKlass().isInitialized()) {
                    field.setCompatibleField(compatibleField);
                }
            }
        }
        return toAdd;
    }

    public Field[] getDeclaredAddedFields() {
        int instanceFieldslength = addedInstanceFields.length;
        int staticFieldsLength = addedStaticFields.length;
        Field[] result = new Field[instanceFieldslength + staticFieldsLength];
        System.arraycopy(addedStaticFields, 0, result, 0, staticFieldsLength);
        System.arraycopy(addedInstanceFields, 0, result, staticFieldsLength, instanceFieldslength);
        return result;
    }

    public Field[] getAddedStaticFields() {
        return addedStaticFields;
    }

    public Field[] getAddedInstanceFields() {
        return addedInstanceFields;
    }

    public Field getStaticFieldAtSlot(int slot) throws IndexOutOfBoundsException {
        Field field = binarySearch(addedStaticFields, slot);
        if (field != null) {
            return field;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IndexOutOfBoundsException("index out of range: " + slot);
        }
    }

    public Field getInstanceFieldAtSlot(int slot) {
        return binarySearch(addedInstanceFields, slot);
    }

    private static Field binarySearch(Field[] arr, int slot) {
        int firstIndex = 0;
        int lastIndex = arr.length - 1;

        while (firstIndex <= lastIndex) {
            int middleIndex = (firstIndex + lastIndex) / 2;

            if (arr[middleIndex].getSlot() == slot) {
                return arr[middleIndex];
            } else if (arr[middleIndex].getSlot() > slot) {
                firstIndex = middleIndex + 1;
            } else if (arr[middleIndex].getSlot() < slot) {
                lastIndex = middleIndex - 1;
            }
        }
        return null;
    }
}
