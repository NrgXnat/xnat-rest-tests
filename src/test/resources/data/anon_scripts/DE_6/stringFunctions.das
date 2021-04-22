version "6.1"
(0010,1000) := format["{0}_{1}_{2}_{3}", lowercase[(0008,0060)], uppercase[(0008,103E)], replace[substring["0123456789", 0, 8], "3", "0"], concatenate["10", "20", "30"]]