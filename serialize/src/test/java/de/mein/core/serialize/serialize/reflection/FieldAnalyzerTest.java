package de.mein.core.serialize.serialize.reflection;

import de.mein.core.serialize.serialize.reflection.classes.ReflectionTestPojo;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by xor on 01.11.2015.
 */
public class FieldAnalyzerTest {

    private Field fPrimitive;
    private Field fPrimitiveCollection;
    private Field fEntity;
    private Field fEntityCollection;
    private Field fTwoDimensionalList;

    private ReflectionTestPojo object;

    @Before
    public void prepare() {
        object = new ReflectionTestPojo();
        List<Field> fields = FieldAnalyzer.collectFields(object.getClass());
        this.fPrimitive = fields.stream().filter(f -> f.getName().equals("primitive")).findFirst().get();
        this.fPrimitiveCollection = fields.stream().filter(field -> field.getName().equals("primitiveCollection")).findFirst().get();
        this.fEntity = fields.stream().filter(field -> field.getName().equals("entity")).findFirst().get();
        this.fEntityCollection = fields.stream().filter(field -> field.getName().equals("entityCollection")).findFirst().get();
        this.fTwoDimensionalList = fields.stream().filter(field -> field.getName().equals("twoDimensionalList")).findFirst().get();
    }

    @Test
    public void primitive() {
        assertTrue(FieldAnalyzer.isPrimitive(fPrimitive));
        assertFalse(FieldAnalyzer.isEntitySerializable(fPrimitive));
        assertFalse(FieldAnalyzer.isPrimitiveCollection(fPrimitive));
        assertFalse(FieldAnalyzer.isEntitySerializableCollection(fPrimitive));
    }

    @Test
    public void primitiveCollection() {
        assertFalse(FieldAnalyzer.isPrimitive(fPrimitiveCollection));
        assertFalse(FieldAnalyzer.isEntitySerializable(fPrimitiveCollection));
        assertTrue(FieldAnalyzer.isPrimitiveCollection(fPrimitiveCollection));
        assertFalse(FieldAnalyzer.isEntitySerializableCollection(fPrimitiveCollection));
    }

    @Test
    public void entitySerializable() {
        assertFalse(FieldAnalyzer.isPrimitive(fEntity));
        assertTrue(FieldAnalyzer.isEntitySerializable(fEntity));
        assertFalse(FieldAnalyzer.isPrimitiveCollection(fEntity));
        assertFalse(FieldAnalyzer.isEntitySerializableCollection(fEntity));
    }

    @Test
    public void entitySerializableCollection() {
        assertFalse(FieldAnalyzer.isPrimitive(fEntityCollection));
        assertFalse(FieldAnalyzer.isEntitySerializable(fEntityCollection));
        assertFalse(FieldAnalyzer.isPrimitiveCollection(fEntityCollection));
        assertTrue(FieldAnalyzer.isEntitySerializableCollection(fEntityCollection));
        assertFalse(FieldAnalyzer.isPrimitiveCollection(fEntityCollection));
        assertFalse(FieldAnalyzer.isPrimitiveCollection(fTwoDimensionalList));
    }
}
