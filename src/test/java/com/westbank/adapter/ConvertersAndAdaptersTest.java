package com.westbank.adapter;

import com.westbank.adapter.jpa.*;
import com.westbank.adapter.xml.*;
import com.westbank.entity.*;
import org.junit.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConvertersAndAdaptersTest {

    @Test
    public void estateType_roundTripsAndDefaultsToOther() {
        EstateTypeConverter c = new EstateTypeConverter();
        for (EstateType v : EstateType.values())
            assertThat(c.convertToEntityAttribute(c.convertToDatabaseColumn(v))).isEqualTo(v);
        assertThat(c.convertToDatabaseColumn(null)).isEqualTo(0);
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> c.convertToEntityAttribute(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void loanRisk_roundTripsAndDefaultsToNotAssessed() {
        LoanRiskConverter c = new LoanRiskConverter();
        for (LoanRisk v : LoanRisk.values())
            assertThat(c.convertToEntityAttribute(c.convertToDatabaseColumn(v))).isEqualTo(v);
        assertThat(c.convertToDatabaseColumn(null)).isEqualTo(0);
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> c.convertToEntityAttribute(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void maritalStatus_roundTripsAndDefaultsToOther() {
        MaritalStatusConverter c = new MaritalStatusConverter();
        for (MaritalStatus v : MaritalStatus.values())
            assertThat(c.convertToEntityAttribute(c.convertToDatabaseColumn(v))).isEqualTo(v);
        assertThat(c.convertToDatabaseColumn(null)).isEqualTo(0);
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> c.convertToEntityAttribute(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void residenceType_roundTripsAndDefaultsToOther() {
        ResidenceTypeConverter c = new ResidenceTypeConverter();
        for (ResidenceType v : ResidenceType.values())
            assertThat(c.convertToEntityAttribute(c.convertToDatabaseColumn(v))).isEqualTo(v);
        assertThat(c.convertToDatabaseColumn(null)).isEqualTo(0);
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> c.convertToEntityAttribute(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void loanFileStatus_roundTripsAndKeepsNull() {
        LoanFileStatusConverter c = new LoanFileStatusConverter();
        for (LoanFileStatus v : LoanFileStatus.values())
            assertThat(c.convertToEntityAttribute(c.convertToDatabaseColumn(v))).isEqualTo(v);
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> c.convertToEntityAttribute(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void xmlAdapters_convertBothWaysAndTreatEmptyAsNull() throws Exception {
        assertThat(new DoubleAdapter().unmarshal("1.5")).isEqualTo(1.5);
        assertThat(new DoubleAdapter().unmarshal("")).isNull();
        assertThat(new DoubleAdapter().unmarshal(null)).isNull();
        assertThat(new DoubleAdapter().marshal(1.5)).isEqualTo("1.5");
        assertThat(new DoubleAdapter().marshal(null)).isNull();

        assertThat(new FloatAdapter().unmarshal("2.5")).isEqualTo(2.5f);
        assertThat(new FloatAdapter().unmarshal("")).isNull();
        assertThat(new FloatAdapter().marshal(2.5f)).isEqualTo("2.5");
        assertThat(new FloatAdapter().marshal(null)).isNull();

        assertThat(new IntegerAdapter().unmarshal("42")).isEqualTo(42);
        assertThat(new IntegerAdapter().unmarshal("")).isNull();
        assertThat(new IntegerAdapter().marshal(42)).isEqualTo("42");
        assertThat(new IntegerAdapter().marshal(null)).isNull();

        assertThat(new LongAdapter().unmarshal("42")).isEqualTo(42L);
        assertThat(new LongAdapter().unmarshal("")).isNull();
        assertThat(new LongAdapter().marshal(42L)).isEqualTo("42");
        assertThat(new LongAdapter().marshal(null)).isNull();

        assertThat(new LocalDateAdapter().unmarshal("2019-01-31")).isEqualTo(LocalDate.of(2019, 1, 31));
        assertThat(new LocalDateAdapter().unmarshal("")).isNull();
        assertThat(new LocalDateAdapter().marshal(LocalDate.of(2019, 1, 31))).isEqualTo("2019-01-31");
        assertThat(new LocalDateAdapter().marshal(null)).isNull();
    }
}
