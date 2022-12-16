version "6.3"
if ((2001,{Philips Imaging DD 001}0c) = "N") {
    (0008,0060) := "if"
    (0008,1080) := "this"
}
elseif ((2001,{Philips Imaging DD 001}0c) = "4") {
    (0008,0005) := "elseif"
}
else {
    (0008,0008) := "else"
}

if ((2005,{Philips MR Imaging DD 004}27) = "FAKE") {
    (0008,0012) := "if"
}
elseif ((2005,{Philips MR Imaging DD 004}27) = "REAL") {
    (0008,0050) := "elseif"
    (0008,103E) := "thisone"
}
else {
    (0008,0013) := "else"
}

if ((2005,{Philips MR Imaging DD 005}16) = "foo") {
    (0008,0020) := "if"
}
elseif ((2005,{Philips MR Imaging DD 005}16) = "bar") {
    (0008,0030) := "elseif"
}
else {
    (0008,1030) := "else"
    (0008,1090) := "nowthis"
}