version "6.4"
modality := (0008,0060)
if ( modality = "MR") {
    retainPrivateTags[ "(2001,{Philips Imaging DD 001}XX)"]
}
elseif ( modality = "CT") {
    (0008,0005) := "elseif"
}
else{
    (0008,0008) := "else"
}