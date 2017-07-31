package org.nrg.testing;

import org.nrg.xdat.bean.base.BaseElement;

import java.util.Comparator;

public class LegacyBaseElementComparator implements Comparator<BaseElement> {

    String field = null;

    public LegacyBaseElementComparator(String f) {
        field = f;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public int compare(BaseElement o1, BaseElement o2) {
        Comparable value1 = null;
        try {
            value1 = (Comparable) o1.getDataFieldValue(field);
        } catch (BaseElement.UnknownFieldException e) {}

        Comparable value2 = null;
        try {
            value2 = (Comparable) o2.getDataFieldValue(field);
        } catch (BaseElement.UnknownFieldException e) {}

        if (value1 == null) {
            if (value2 == null) {
                return 0;
            } else {
                return -1;
            }
        }
        if (value2 == null) {
            return 1;
        }

        return value1.compareTo(value2);
    }

}
