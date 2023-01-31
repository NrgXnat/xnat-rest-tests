version "6.4"
modality := (0008,0060)
if ( modality = "MR") {
    var := "if"
    (0008,0060) := var
}
elseif ( modality = "CT") {
    var := "elseif"
    (0008,0005) := var
}
else{
    var := "else"
    (0008,0008) := var
}

accessionNumber := (0008,0050)
if ( accessionNumber = "12321") {
    var2 := "4"
}
elseif ( accessionNumber = "20100430") {
    var2 := "2131"
}
else {
    var2 := "11111"
}
(0008,1030):= var2

var3 := "baz"
studyDescription := (0008,1030)
if ( studyDescription = "foo") {
    var3 := "foo"
}
elseif ( studyDescription = "bar") {
    var3 := "bar"
}
else {
    var3 := "foobar"
}
(0008,1090) := var3

contentTime := (0008,0033)
if (contentTime = "131758") {
   var4 := "123456"
}
(0008,0070) := var4