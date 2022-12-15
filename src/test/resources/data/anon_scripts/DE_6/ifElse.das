version "6.3"
modality := (0008,0060)
if( modality = "MR") {
    (0008,0060) := "if"
    (0008,1080) := "this"
}
else {
    (0008,0080) := "else"
}

studyDescription := (0008,1030)
if( studyDescription = "foo") {
    (0008,0070) := "if"
}
else {
    (0008,1030) := "else"
    (0008,1090) := "thisone"
}