package org.jetlinks.collector.converter;

import org.jetlinks.collector.PointData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScalePointDataConverter 单元测试
 * 
 * @author zhouhao
 */
class ScalePointDataConverterTest {

    @Test
    @DisplayName("测试整数类型的整数缩放 - Integer")
    void testIntegerScaleWithIntegerFactor() {
        // 缩放因子为10，不保留小数
        ScalePointDataConverter converter = new ScalePointDataConverter(10.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(100);
        
        PointData result = converter.decode(data);
        
        assertEquals(1000L, result.getParsedData());
        assertTrue(result.getParsedData() instanceof Long);
    }
    
    @Test
    @DisplayName("测试整数类型的整数缩放 - Long")
    void testLongScaleWithIntegerFactor() {
        ScalePointDataConverter converter = new ScalePointDataConverter(100.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(1000L);
        
        PointData result = converter.decode(data);
        
        assertEquals(100000L, result.getParsedData());
        assertTrue(result.getParsedData() instanceof Long);
    }
    
    @Test
    @DisplayName("测试整数类型的整数缩放 - Short")
    void testShortScaleWithIntegerFactor() {
        ScalePointDataConverter converter = new ScalePointDataConverter(2.0, 0);
        
        PointData data = new PointData();
        data.setParsedData((short) 100);
        
        PointData result = converter.decode(data);
        
        assertEquals(200L, result.getParsedData());
        assertTrue(result.getParsedData() instanceof Long);
    }
    
    @Test
    @DisplayName("测试整数类型的整数缩放 - Byte")
    void testByteScaleWithIntegerFactor() {
        ScalePointDataConverter converter = new ScalePointDataConverter(3.0, 0);
        
        PointData data = new PointData();
        data.setParsedData((byte) 10);
        
        PointData result = converter.decode(data);
        
        assertEquals(30L, result.getParsedData());
        assertTrue(result.getParsedData() instanceof Long);
    }
    
    @Test
    @DisplayName("测试整数类型的浮点缩放")
    void testIntegerScaleWithFloatFactor() {
        // 缩放因子为0.5（浮点），会使用浮点缩放
        ScalePointDataConverter converter = new ScalePointDataConverter(0.5, 2);
        
        PointData data = new PointData();
        data.setParsedData(100);
        
        PointData result = converter.decode(data);
        
        assertEquals(50.0, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试浮点类型的缩放 - Float")
    void testFloatScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(1.5, 2);
        
        PointData data = new PointData();
        data.setParsedData(100.0f);
        
        PointData result = converter.decode(data);
        
        assertEquals(150.0, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试浮点类型的缩放 - Double")
    void testDoubleScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(2.5, 2);
        
        PointData data = new PointData();
        data.setParsedData(40.0);
        
        PointData result = converter.decode(data);
        
        assertEquals(100.0, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试BigDecimal的缩放")
    void testBigDecimalScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(1.5, 2);
        
        PointData data = new PointData();
        data.setParsedData(BigDecimal.valueOf(100.0));
        
        PointData result = converter.decode(data);
        
        assertTrue(result.getParsedData() instanceof BigDecimal);
        BigDecimal expected = BigDecimal.valueOf(150.0).setScale(2, RoundingMode.HALF_UP);
        assertEquals(expected, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试BigDecimal不设置精度")
    void testBigDecimalWithoutScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(2.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(BigDecimal.valueOf(50.5));
        
        PointData result = converter.decode(data);
        
        assertTrue(result.getParsedData() instanceof BigDecimal);
        // BigDecimal乘法保留原有精度，50.5 * 2.0 = 101.0，但实际会变成 101.00
        BigDecimal expected = new BigDecimal("101.0");
        assertEquals(0, expected.compareTo((BigDecimal) result.getParsedData()));
    }
    
    @Test
    @DisplayName("测试精度保留 - 四舍五入")
    void testScalePrecision() {
        ScalePointDataConverter converter = new ScalePointDataConverter(1.666, 2);
        
        PointData data = new PointData();
        data.setParsedData(100.0);
        
        PointData result = converter.decode(data);
        
        assertEquals(166.6, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试反向缩放 - 整数类型")
    void testEncodeIntegerType() {
        ScalePointDataConverter converter = new ScalePointDataConverter(10.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(1000L);
        
        PointData result = converter.encode(data);
        
        assertEquals(100L, result.getParsedData());
        assertTrue(result.getParsedData() instanceof Long);
    }
    
    @Test
    @DisplayName("测试反向缩放 - 浮点类型")
    void testEncodeFloatType() {
        ScalePointDataConverter converter = new ScalePointDataConverter(2.5, 2);
        
        PointData data = new PointData();
        data.setParsedData(100.0);
        
        PointData result = converter.encode(data);
        
        assertEquals(40.0, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试反向缩放 - BigDecimal")
    void testEncodeBigDecimal() {
        ScalePointDataConverter converter = new ScalePointDataConverter(2.0, 2);
        
        PointData data = new PointData();
        data.setParsedData(BigDecimal.valueOf(100.0));
        
        PointData result = converter.encode(data);
        
        assertTrue(result.getParsedData() instanceof BigDecimal);
        BigDecimal expected = BigDecimal.valueOf(50.0).setScale(2, RoundingMode.HALF_UP);
        assertEquals(expected, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试正向和反向转换的对称性")
    void testDecodeEncodeSymmetry() {
        ScalePointDataConverter converter = new ScalePointDataConverter(10.0, 0);
        
        PointData original = new PointData();
        original.setParsedData(100);
        
        // 先正向转换
        PointData encoded = converter.decode(original);
        assertEquals(1000L, encoded.getParsedData());
        
        // 再反向转换
        PointData decoded = converter.encode(encoded);
        assertEquals(100L, decoded.getParsedData());
    }
    
    @Test
    @DisplayName("测试浮点类型的对称性")
    void testFloatDecodeEncodeSymmetry() {
        ScalePointDataConverter converter = new ScalePointDataConverter(2.5, 2);
        
        PointData original = new PointData();
        original.setParsedData(40.0);
        
        // 先正向转换
        PointData encoded = converter.decode(original);
        assertEquals(100.0, encoded.getParsedData());
        
        // 再反向转换
        PointData decoded = converter.encode(encoded);
        assertEquals(40.0, decoded.getParsedData());
    }
    
    @Test
    @DisplayName("测试非数字类型数据 - 不进行转换")
    void testNonNumericData() {
        ScalePointDataConverter converter = new ScalePointDataConverter(10.0, 0);
        
        PointData data = new PointData();
        data.setParsedData("not a number");
        
        PointData result = converter.decode(data);
        
        // 非数字类型应该保持不变
        assertEquals("not a number", result.getParsedData());
    }
    
    @Test
    @DisplayName("测试null值")
    void testNullData() {
        ScalePointDataConverter converter = new ScalePointDataConverter(10.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(null);
        
        PointData result = converter.decode(data);
        
        assertNull(result.getParsedData());
    }
    
    @Test
    @DisplayName("测试大数值避免溢出")
    void testLargeNumberNoOverflow() {
        ScalePointDataConverter converter = new ScalePointDataConverter(1000.0, 0);
        
        PointData data = new PointData();
        // 使用一个较大的int值
        data.setParsedData(1000000);
        
        PointData result = converter.decode(data);
        
        // 结果应该是1000000000L，而不是溢出的int
        assertEquals(1000000000L, result.getParsedData());
        assertTrue(result.getParsedData() instanceof Long);
    }
    
    @Test
    @DisplayName("测试极大数值")
    void testVeryLargeNumber() {
        ScalePointDataConverter converter = new ScalePointDataConverter(2.0, 0);
        
        PointData data = new PointData();
        // 使用Long的一个较大值
        long inputValue = 1000000000000L; // 1万亿
        data.setParsedData(inputValue);
        
        PointData result = converter.decode(data);
        
        assertEquals(2000000000000L, result.getParsedData());
        assertTrue(result.getParsedData() instanceof Long);
    }
    
    @Test
    @DisplayName("测试负数缩放")
    void testNegativeNumberScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(10.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(-50);
        
        PointData result = converter.decode(data);
        
        assertEquals(-500L, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试零值缩放")
    void testZeroScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(100.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(0);
        
        PointData result = converter.decode(data);
        
        assertEquals(0L, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试小数缩放因子")
    void testSmallFactorScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(0.01, 4);
        
        PointData data = new PointData();
        data.setParsedData(1234.0);
        
        PointData result = converter.decode(data);
        
        assertEquals(12.34, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试温度传感器场景 - 原始值需要除以10")
    void testTemperatureSensorScenario() {
        // 温度传感器返回235，实际温度是23.5℃
        ScalePointDataConverter converter = new ScalePointDataConverter(0.1, 1);
        
        PointData data = new PointData();
        data.setParsedData(235);
        
        PointData result = converter.decode(data);
        
        assertEquals(23.5, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试压力传感器场景 - 需要乘以缩放系数")
    void testPressureSensorScenario() {
        // 压力传感器返回原始值100，需要乘以系数1.5得到实际压力
        ScalePointDataConverter converter = new ScalePointDataConverter(1.5, 2);
        
        PointData data = new PointData();
        data.setParsedData(100.0);
        
        PointData result = converter.decode(data);
        
        assertEquals(150.0, result.getParsedData());
    }
    
    @ParameterizedTest
    @MethodSource("provideScaleTestCases")
    @DisplayName("参数化测试 - 多种类型和因子组合")
    void testVariousScaleCombinations(Number input, double factor, int scale, Object expectedOutput) {
        ScalePointDataConverter converter = new ScalePointDataConverter(factor, scale);
        
        PointData data = new PointData();
        data.setParsedData(input);
        
        PointData result = converter.decode(data);
        
        if (expectedOutput instanceof Double) {
            assertEquals((Double) expectedOutput, (Double) result.getParsedData(), 0.001);
        } else {
            assertEquals(expectedOutput, result.getParsedData());
        }
    }
    
    private static Stream<Arguments> provideScaleTestCases() {
        return Stream.of(
            // 整数类型 + 整数因子
            Arguments.of(100, 10.0, 0, 1000L),
            Arguments.of(50L, 2.0, 0, 100L),
            Arguments.of((short) 20, 5.0, 0, 100L),
            Arguments.of((byte) 10, 3.0, 0, 30L),
            
            // 整数类型 + 浮点因子
            Arguments.of(100, 1.5, 2, 150.0),
            Arguments.of(200, 0.5, 1, 100.0),
            
            // 浮点类型
            Arguments.of(100.0f, 2.5, 2, 250.0),
            Arguments.of(50.0, 3.0, 1, 150.0),
            
            // 负数
            Arguments.of(-100, 10.0, 0, -1000L),
            Arguments.of(-50.0, 2.0, 1, -100.0),
            
            // 零
            Arguments.of(0, 100.0, 0, 0L),
            Arguments.of(0.0, 10.0, 2, 0.0)
        );
    }
    
    @Test
    @DisplayName("测试equals - 相同参数应该相等")
    void testEquals() {
        ScalePointDataConverter converter1 = new ScalePointDataConverter(10.0, 2);
        ScalePointDataConverter converter2 = new ScalePointDataConverter(10.0, 2);
        
        assertEquals(converter1, converter2);
    }
    
    @Test
    @DisplayName("测试equals - 不同参数应该不相等")
    void testNotEquals() {
        ScalePointDataConverter converter1 = new ScalePointDataConverter(10.0, 2);
        ScalePointDataConverter converter2 = new ScalePointDataConverter(20.0, 2);
        
        assertNotEquals(converter1, converter2);
    }
    
    @Test
    @DisplayName("测试hashCode - 相同参数应该有相同的hashCode")
    void testHashCode() {
        ScalePointDataConverter converter1 = new ScalePointDataConverter(10.0, 2);
        ScalePointDataConverter converter2 = new ScalePointDataConverter(10.0, 2);
        
        assertEquals(converter1.hashCode(), converter2.hashCode());
    }
    
    @Test
    @DisplayName("测试整数除法 - 反向缩放")
    void testIntegerDivisionInEncode() {
        ScalePointDataConverter converter = new ScalePointDataConverter(3.0, 0);
        
        PointData data = new PointData();
        data.setParsedData(100L);
        
        PointData result = converter.encode(data);
        
        // 100 / 3 = 33 (整数除法)
        assertEquals(33L, result.getParsedData());
    }
    
    @Test
    @DisplayName("测试带小数位的反向缩放")
    void testEncodeWithScale() {
        ScalePointDataConverter converter = new ScalePointDataConverter(3.0, 2);
        
        PointData data = new PointData();
        data.setParsedData(100.0);
        
        PointData result = converter.encode(data);
        
        // 100 / 3 = 33.33
        assertEquals(33.33, (Double) result.getParsedData(), 0.01);
    }
    
    @Test
    @DisplayName("测试BigDecimal精度保持")
    void testBigDecimalPrecisionMaintained() {
        ScalePointDataConverter converter = new ScalePointDataConverter(1.111, 3);
        
        PointData data = new PointData();
        data.setParsedData(BigDecimal.valueOf(100.0));
        
        PointData result = converter.decode(data);
        
        assertTrue(result.getParsedData() instanceof BigDecimal);
        BigDecimal expected = new BigDecimal("111.100");
        assertEquals(expected, result.getParsedData());
    }
}

