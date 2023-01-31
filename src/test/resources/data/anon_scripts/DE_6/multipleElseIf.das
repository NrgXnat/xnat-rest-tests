version "6.4"
modality := (0008,0060)
if( modality = "MR") {
    (0008,0005) := "if"
    (0008,0012) := "this"
}
elseif( modality = "MR") {
    (0008,0008) := "elseif1"
}
elseif( modality = "PET") {
    (0008,0013) := "elseif2"
}
elseif( modality = "RAD") {
    (0018,0087) := "elseif3"
}
else{
    (0008,0014) := "else"
}

accessionNumber := (0008,0050)
if( accessionNumber = "1") {
    (0008,0020) := "if"
}
elseif( accessionNumber = "20100430") {
    (0008,0021) := "elseif1"
    (0008,0022) := "thisone"
}
elseif( accessionNumber = "20100430") {
    (0008,002a) := "elseif2"
}
elseif( accessionNumber = "32431") {
    (0018,0088) := "elseif3"
}
else{
    (0008,0030) := "else"
}

studyDescription := (0008,1030)
if( studyDescription = "foo") {
    (0008,0031) := "if"
}
elseif( studyDescription = "bar") {
    (0008,0033) := "elseif1"
}
elseif( studyDescription = "Fruit_Struct") {
    (0008,0050) := "elseif2"
    (0008,0060) := "nowthis"
}
elseif( studyDescription = "lala") {
    (0018,0095) := "elseif3"
}
else{
    (0008,0070) := "else"
}

contentTime := (0008,0033)
if( contentTime = "23984") {
    (0018,1000) := "if"
}
elseif( contentTime = "2321") {
    (0018,1020) := "elseif1"
}
elseif( contentTime = "hello") {
    (0018,1030) := "elseif2"
}
elseif( contentTime = "131758") {
    (0018,5100) := "elseif3"
    (0018,9004) := "thenthis"
}
else{
    (0018,9005) := "else"
}

seriesDescription := (0008,103e)
if( seriesDescription = "foo") {
    (0008,0080) := "if"
}
elseif( seriesDescription = "bar") {
    (0008,0102) := "elseif1"
}
elseif( seriesDescription = "Fruit_Struct") {
    (0008,1010) := "elseif2"
}
elseif( seriesDescription = "123") {
    (0018,9008) := "elseif3"
}
else{
    (0008,1030) := "else"
    (0008,103e) := "here"
}