version "6.3"
modality := (0008,0060)
if ( modality != "CT") {
    (0008,0060) := "if"
    (0008,1080) := "this"
}
elseif ( modality = "MR") {
    (0008,0005) := "elseif"
}
else{
    (0008,0008) := "else"
}

accessionNumber := (0008,0050)
if ( accessionNumber != "20100430") {
    (0008,0012) := "if"
}
elseif ( accessionNumber != "4324") {
    (0008,0050) := "elseif"
    (0008,103E) := "thisone"
}
else {
    (0008,0013) := "else"
}

studyDescription := (0008,1030)
if ( studyDescription != "Fruit_Struct") {
    (0008,0020) := "if"
}
elseif ( studyDescription = "bar") {
    (0008,0021) := "elseif"
}
else {
    (0008,1030) := "else"
    (0008,1090) := "nowthis"
}