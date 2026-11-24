package com.westbank.adapter.xml;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class LongAdapter extends XmlAdapter<String, Long> {

    public Long unmarshal(String v) {
        if (v != null && !v.isEmpty())
            return jakarta.xml.bind.DatatypeConverter.parseLong(v);
        return null;
    }

    public String marshal(Long v) {
        if (v != null)
            return jakarta.xml.bind.DatatypeConverter.printLong(v);
        return null;
    }

}
