version "6.3"
modality := (0008,0060)
if ( modality = "MR") {
    alterPixels["rectangle", "l=10, t=10, r=20, b=100", "solid", "v=100"]
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
    alterPixels["rectangle", "l=90, t=10, r=100, b=100", "solid", "v=100"]
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
    alterPixels["rectangle", "l=25, t=10, r=75, b=40", "solid", "v=100"]
}