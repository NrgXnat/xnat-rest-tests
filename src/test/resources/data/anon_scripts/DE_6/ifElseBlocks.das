version "6.3"
modality := (0008,0060)
if( modality = "MR") {
    (0008,0060) := "if"
    (0008,1080) := "this"
}
elseif ( modality = "CT") {
    (0008,0060) := "elseif"
}
else {
    (0008,0060) := "else"
}

accessionNumber := (0008,0050)
if( accessionNumber = "1") {
    (0008,0050) := "if"
}
elseif ( accessionNumber = "20100430") {
    (0008,0050) := "elseif"
    (0008,103E) := "thisone"
}
else {
    (0008,0050) := "else"
}

studyDescription := (0008,1030)
if( studyDescription = "foo") {
    (0008,1030) := "if"
}
elseif ( studyDescription = "bar") {
    (0008,1030) := "elseif"
}
else {
    (0008,1030) := "else"
    (0008,1090) := "nowthis"
}