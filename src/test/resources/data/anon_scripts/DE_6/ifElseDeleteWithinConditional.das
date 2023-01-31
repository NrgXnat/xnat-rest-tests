version "6.4"
modality := (0008,0060)
if ( modality = "MR") {
    -(0008,0020)
    -(0008,1080)
}
elseif ( modality = "CT") {
    (0008,0005) := "elseif"
}
else{
    (0008,0008) := "else"
}

accessionNumber := (0008,0050)
if ( accessionNumber = "123") {
    (0008,0012) := "if"
}
elseif ( accessionNumber = "20100430") {
    -(0008,0050)
    -(0008,103E)
}
else {
    (0008,0013) := "else"
}

studyDescription := (0008,1030)
if ( studyDescription = "foo") {
    (0008,0060) := "if"
}
elseif ( studyDescription = "bar") {
    (0008,0021) := "elseif"
}
else {
    -(0008,0031)
    -(0008,1090)
}