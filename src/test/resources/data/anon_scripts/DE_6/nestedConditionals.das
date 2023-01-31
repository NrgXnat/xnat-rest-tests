version "6.4"
modality := (0008,0060)
accessionNumber := (0008,0050)
studyDescription := (0008,1030)
seriesDescription := (0008,103e)
if ( modality = "MR") {
    if ( accessionNumber != "20100430") {
        (0008,0060) := "if"
    }
    else{
        (0008,1030) := "else"
        (0008,1090) := "nowthis"
        if ( studyDescription = "Fruit_Struct"){
            if ( seriesDescription = "PDW_TSE"){
                (0008,0050) := "if"
                (0008,103E) := "thisone"
            }
        }
    }
}
elseif ( modality = "MR") {
    (0008,0005) := "elseif"
}
else{
    (0008,0008) := "else"
}
