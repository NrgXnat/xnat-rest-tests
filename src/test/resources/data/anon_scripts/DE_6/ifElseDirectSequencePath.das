version "6.4"
if ((0010,1002)[0]/(0010,0020) = "PATIENT_0001") {
    (0008,0060) := "if"
    (0008,1080) := "this"
}
elseif ((0010,1002)[0]/(0010,0020) = "PATIENT_4") {
    (0008,0005) := "elseif"
}
else {
    (0008,0008) := "else"
}

if ((0018,0026)[1]/(0018,0028) = "FAKE") {
    (0008,0012) := "if"
}
elseif ((0018,0026)[1]/(0018,0028) = "100000") {
    (0008,0050) := "elseif"
    (0008,103e) := "thisone"
}
else {
    (0008,0013) := "else"
}

if ((2001,{Philips Imaging DD 001}5f)[0]/(2001,{Philips MR Imaging DD 001}33) = "foo") {
    (0008,0020) := "if"
}
elseif ((2001,{Philips Imaging DD 001}5f)[0]/(2001,{Philips MR Imaging DD 001}33) = "bar") {
    (0008,0030) := "elseif"
}
else {
    (0008,1030) := "else"
    (0008,1090) := "nowthis"
}