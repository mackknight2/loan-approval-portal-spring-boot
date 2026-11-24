package com.westbank.adapter.xml;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class FloatAdapter extends XmlAdapter<String, Float> {

    public Float unmarshal(String v) {
        if (v != null && !v.isEmpty())
            return jakarta.xml.bind.DatatypeConverter.parseFloat(v);
        return null;
    }

    public String marshal(Float v) {
        if (v != null)
            return jakarta.xml.bind.DatatypeConverter.printFloat(v);
        return null;
    }

}
