version "6.1"
(0010,1000) := format["{0}_{1}_{2}_102030", lowercase[(0008,0060)], uppercase[(0008,103E)], replace[substring["0123456789", 0, 8], "3", "0"]]