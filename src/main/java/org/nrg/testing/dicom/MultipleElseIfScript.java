package org.nrg.testing.dicom;

public class MultipleElseIfScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0005)", "if");
        root.putValueEqualCheck("(0008,0012)", "this");
        root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
        root.putValueEqualCheck("(0008,0013)", "131758");
        root.putValueEqualCheck("(0008,0014)", "1.3.46.670589.11.5730.5");
        root.putValueEqualCheck("(0008,0020)", "20100430");
        root.putValueEqualCheck("(0008,0021)", "elseif1");
        root.putValueEqualCheck("(0008,0022)", "thisone");
        root.putValueEqualCheck("(0008,002a)", "20100430130441.40");
        root.putValueEqualCheck("(0008,0030)", "123437");
        root.putValueEqualCheck("(0008,0031)", "130441.40");
        root.putValueEqualCheck("(0008,0033)", "131758");
        root.putValueEqualCheck("(0008,0050)", "elseif2");
        root.putValueEqualCheck("(0008,0060)", "nowthis");
        root.putValueEqualCheck("(0008,0070)", "Philips Medical Systems");
        root.putValueEqualCheck("(0008,0080)", "BU SCHOOL OF MEDICINE");
        root.putValueEqualCheck("(0008,0102)", "DCM");
        root.putValueEqualCheck("(0008,1010)", "PHILIPS-13EFFD8");
        root.putValueEqualCheck("(0008,1030)", "else");
        root.putValueEqualCheck("(0008,103e)", "here");
        root.putValueEqualCheck("(0018,0087)", "3");
        root.putValueEqualCheck("(0018,0088)", "2");
        root.putValueEqualCheck("(0018,0095)", "226");
        root.putValueEqualCheck("(0018,1000)", "05730");
        root.putValueEqualCheck("(0018,1020)", "2.6.3\\2.6.3.4");
        root.putValueEqualCheck("(0018,1030)", "PDW_TSE CLEAR");
        root.putValueEqualCheck("(0018,5100)", "elseif3");
        root.putValueEqualCheck("(0018,9004)", "thenthis");
        root.putValueEqualCheck("(0018,9005)", "TSE");
        root.putValueEqualCheck("(0018,9008)", "SPIN");

        return root;
    }

}
