package com.westbank.adapter.xml;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class IntegerAdapter extends XmlAdapter<String, Integer> {

    public Integer unmarshal(String v) {
        if (v != null && !v.isEmpty())
            return jakarta.xml.bind.DatatypeConverter.parseInt(v);
        return null;
    }

    public String marshal(Integer v) {
        if (v != null)
            return jakarta.xml.bind.DatatypeConverter.printInt(v);
        return null;
    }

}
