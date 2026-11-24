package com.westbank.helper;

import com.westbank.ws.test._2019._01.TestRequest;
import org.junit.Test;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HelpersTest {

    @Test
    public void dateUtil_convertsDates() {
        Date date = new GregorianCalendar(2019, Calendar.JANUARY, 31).getTime();
        XMLGregorianCalendar xml = DateUtil.convert(date);
        assertThat(xml.getYear()).isEqualTo(2019);
        assertThat(xml.getMonth()).isEqualTo(1);
        assertThat(xml.getDay()).isEqualTo(31);
        assertThat(DateUtil.convert((Date) null)).isNull();
        assertThat(DateUtil.convert((GregorianCalendar) null)).isNull();
        assertThat(DateUtil.convertDate(date)).isEqualTo("2019-01-31");
    }

    @Test
    public void passwordUtil_isAnUnfinishedStub_currentBehaviour() {
        // Known gap (pinned, not fixed): password hashing is not implemented.
        assertThat(PasswordUtil.encode("abcd1234")).isNull();
        assertThat(PasswordUtil.matches("abcd1234", "abcd1234")).isFalse();
    }

    @Test
    public void jaxbUtil_marshalsJaxbObjectsToXml() {
        TestRequest request = new TestRequest();
        assertThat(JaxbUtil.toXml(request)).contains("TestRequest");
        assertThat(JaxbUtil.toXml(null)).isEmpty();
    }

    @Test
    public void xmlUtil_indentsXml() {
        assertThat(XmlUtil.format("<a><b>x</b></a>")).contains("<a>").contains("  <b>x</b>");
        assertThatThrownBy(() -> XmlUtil.format("not xml")).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void debugger_printsCollections() {
        Debugger.print(Arrays.asList("a", "b"));
    }
}
