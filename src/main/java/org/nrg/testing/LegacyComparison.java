package org.nrg.testing;

import com.google.common.base.Joiner;
import org.apache.log4j.Logger;
import org.nrg.xdat.bean.base.BaseElement;
import org.nrg.xdat.bean.base.BaseElement.UnknownFieldException;
import org.nrg.xdat.bean.reader.XDATXMLReader;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.fail;

public class LegacyComparison {

    private static final Logger LOGGER = Logger.getLogger(LegacyComparison.class);
    public List<String> critical = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();

    public static BaseElement readElementFromReponse(InputStream response) throws IOException, SAXException {
        return new XDATXMLReader().parse(response);
    }

    public static LegacyComparison compareObjectsFromFile(File f1, File f2, Map<Class<? extends BaseElement>, List<String>> ignoreMap) throws IOException, SAXException {
        FileInputStream fis = new FileInputStream(f1);
        XDATXMLReader reader = new XDATXMLReader();
        BaseElement base1 = reader.parse(fis);

        FileInputStream fis2 = new FileInputStream(f2);
        XDATXMLReader reader2 = new XDATXMLReader();
        BaseElement base2 = reader2.parse(fis2);

        return compareObjects(base1, base2, new LegacyComparison(), ignoreMap);
    }

    @SuppressWarnings("unchecked") // Can't do anything about BaseElement#getDataFieldValue returning either a BaseElement or ArrayList<BaseElement>
    public static LegacyComparison compareObjects(BaseElement base1, BaseElement base2, LegacyComparison msgs, Map<Class<? extends BaseElement>, List<String>> ignoreMap) {
        if (!base1.getSchemaElementName().equals(base2.getSchemaElementName())) {
            msgs.critical.add("Different XSI Types: " + base1.getSchemaElementName() + " - " + base2.getSchemaElementName());
        } else {
            List<String> ignoreFields = new ArrayList<>();
            for (Map.Entry<Class<? extends BaseElement>, List<String>> entry : ignoreMap.entrySet()) {
                if (entry.getKey().isInstance(base1)) {
                    ignoreFields.addAll(entry.getValue());
                    break;
                }
            }

            for (int i = 0; i < base1.getAllFields().size(); i++) {
                try {
                    String path = (String) base1.getAllFields().get(i);
                    if (!ignoreFields.contains(path)) {
                        String ft = base1.getFieldType(path);
                        if (ft.equals(BaseElement.field_data) || ft.equals(BaseElement.field_LONG_DATA)) {
                            if (path.equals("ID") || path.equals("image_session_ID") || path.equals("subject_ID") || path.equals("imageSession_ID")) {
                                Object v1 = base1.getDataFieldValue(path);
                                Object v2 = base2.getDataFieldValue(path);
                                if (isNonEmpty(v1) && isNonEmpty(v2)) {
                                    if (!v1.equals(v2)) {
                                        msgs.critical.add(base1.getSchemaElementName() + "/" + path + " Mismatch: " + v1 + " " + v2);
                                    }
                                } else if (isNonEmpty(v1) || isNonEmpty(v1)) {
                                    msgs.warnings.add(base1.getSchemaElementName() + " Null ID: " + v1 + " " + v2);
                                }
                            } else {
                                Object v1 = base1.getDataFieldValue(path);
                                Object v2 = base2.getDataFieldValue(path);
                                if (v1 != null && v2 != null) {
                                    if (v1 instanceof String) {
                                        v1 = ((String) v1).replaceAll(" ", "").replaceAll("\n", "").replaceAll("\t", "");
                                    }
                                    if (v2 instanceof String) {
                                        v2 = ((String) v2).replaceAll(" ", "").replaceAll("\n", "").replaceAll("\t", "");
                                    }
                                    if (!v1.equals(v2)) {
                                        msgs.critical.add(base1.getSchemaElementName() + "/" + path + " Mismatch: " + v1 + " " + v2);
                                    }
                                } else if (v1 != null || v2 != null) {
                                    msgs.critical.add(base1.getSchemaElementName() + "/" + path + " Mismatch: " + v1 + " " + v2);
                                }
                            }
                        } else {
                            //reference
                            Object o1 = base1.getReferenceField(path);
                            Object o2 = base2.getReferenceField(path);

                            if (o1 == null && o2 == null) {

                            } else if (o1 == null || o2 == null) {
                                msgs.critical.add(path + " Mismatch: " + o1 + " " + o2);
                            } else {
                                if (o1 instanceof ArrayList && o2 instanceof ArrayList) {
                                    ArrayList<BaseElement> children1 = (ArrayList<BaseElement>) o1;
                                    ArrayList<BaseElement> children2 = (ArrayList<BaseElement>) o2;
                                    if (children1.size() != children2.size()) {
                                        msgs.critical.add(base1.getSchemaElementName() + "/" + path + " Count mis-match: " + children1.size() + " " + children2.size());
                                    } else {
                                        if (children1.size() > 0) {
                                            String field = null;
                                            try {
                                                children1.get(0).getFieldType("ID");
                                                field = "ID";
                                            } catch (UnknownFieldException e) {
                                                try {
                                                    children1.get(0).getFieldType("project");
                                                    field = "project";
                                                } catch (UnknownFieldException e2) {
                                                    try {
                                                        children1.get(0).getFieldType("timestamp");
                                                        field = "timestamp";
                                                    } catch (UnknownFieldException e3) {

                                                        try {
                                                            children1.get(0).getFieldType("URI");
                                                            field = "URI";
                                                        } catch (UnknownFieldException e4) {

                                                            try {
                                                                children1.get(0).getFieldType("path");
                                                                field = "path";
                                                            } catch (UnknownFieldException e5) {

                                                                try {
                                                                    children1.get(0).getFieldType("name");
                                                                    field = "name";
                                                                } catch (UnknownFieldException e6) {
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (field != null) {
                                                children1.sort(new LegacyBaseElementComparator(field));
                                                children2.sort(new LegacyBaseElementComparator(field));
                                            }

                                            for (int j = 0; j < children1.size(); j++) {
                                                compareObjects(children1.get(j), children2.get(j), msgs, ignoreMap);
                                            }
                                        }
                                    }
                                } else if (o1 instanceof ArrayList || o2 instanceof ArrayList) {
                                    msgs.critical.add(base1.getSchemaElementName() + "/" + path + " Type mis-match: " + o1 + " " + o2);
                                } else {
                                    BaseElement child1 = (BaseElement) o1;
                                    BaseElement child2 = (BaseElement) o2;

                                    compareObjects(child1, child2, msgs, ignoreMap);
                                }
                            }
                        }
                    }
                } catch (BaseElement.UnknownFieldException e) {
                    LOGGER.error("A specified field was not found", e);
                }

            }
        }

        return msgs;
    }

    public static void compareBeanXML(File f1, File f2, Map<Class<? extends BaseElement>, List<String>> fieldsToIgnore) {
        try {
            final LegacyComparison comparison = compareObjectsFromFile(f1, f2, fieldsToIgnore);
            if (comparison.critical.size() == 0) {
                for (String warning : comparison.warnings) {
                    LOGGER.warn(warning);
                }
            } else {
                fail(Joiner.on(", ").join(comparison.critical));
            }
        } catch (Exception e) {
            fail("Failed to compare files: " + e);
        }
    }

    private static boolean isNonEmpty(Object o) {
        return o != null && !o.toString().equals("");
    }

}
