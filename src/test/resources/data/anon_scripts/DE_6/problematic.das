//
// Very slightly modified from the original post on the discussion group at
// https://groups.google.com/g/xnat_discussion/c/gUCbuU1ZDeg .
// This script is intended to provide an appropriate test for the tickets
// DE-182 : Poor performance on removing inline comments
// and
// DE-183 : Poor performance on constructing tag delete functionality
//
//
version "6.1"
(0008,0014) := hashUID[(0008,0014)]  //Hash Instance Creator UID
(0008,0018) := hashUID[(0008,0018)]  //Hash SOP Instance UID
(0008,0050) := hashUID[(0008,0050)]  //Hash Accession Number
(0008,0058) := hashUID[(0008,0058)]  //Hash Failed SOP Instance UID List
(0008,010c) := hashUID[(0008,010c)]  //Hash Private Coding Scheme Creator UID
(0008,010d) := hashUID[(0008,010d)]  //Hash Code Set Extension Creator UID
(0008,1155) := hashUID[(0008,1155)]  //Hash Ref SOP Instance UID
(0008,1195) := hashUID[(0008,1195)]  //Hash Transaction UID
(0008,3010) := hashUID[(0008,3010)]  //Hash Irradiation Event UID
(0008,9123) := hashUID[(0008,9123)]  //Hash Creator Version UID
(0012,0040) := hashUID[(0012,0040)]  //Hash Clinical Trial Subject ID
(0020,000d) := hashUID[(0020,000d)]  //Hash Study Instance UID
(0020,000e) := hashUID[(0020,000e)]  //Hash Series Instance UID
(0020,0052) := hashUID[(0020,0052)]  //Hash Frame Of Reference UID
(0020,0200) := hashUID[(0020,0200)]  //Hash Synchronization Frame Of Reference UID
(0020,9161) := hashUID[(0020,9161)]  //Hash Concatenation UID
(0020,9164) := hashUID[(0020,9164)]  //Hash Dimension Organization UID
(0028,1199) := hashUID[(0028,1199)]  //Hash Palette Color LUT UID
(0028,1214) := hashUID[(0028,1214)]  //Hash Large Palette Color LUT Uid
(0040,4023) := hashUID[(0040,4023)]  //Hash Ref Gen Purpose Sched Proc Step Trans UID
(0040,a124) := hashUID[(0040,a124)]  //Hash UID
(0040,db0c) := hashUID[(0040,db0c)]  //Hash Template Extension Organization UID
(0040,db0d) := hashUID[(0040,db0d)]  //Hash Template Extension Creator UID
(0088,0140) := hashUID[(0088,0140)]  //Hash Storage Media Fileset UID

- (0008,0080)  //Remove Institution Name
- (0008,0081)  //Remove Institution Address
- (0008,0082)  //Remove Institution Code Seq
- (0008,0090)  //Remove Referring Physician Name
- (0008,0092)  //Remove Referring Physician Address
- (0008,0094)  //Remove Referring Physician Phone Numbers
- (0008,0096)  //Remove Referring Physicians ID Seq
- (0008,0201)  //Remove Timezone Offset From UTC
- (0008,1010)  //Remove Station Name
- (0008,1040)  //Remove Institutional Department Name
- (0008,1048)  //Remove Physician Of Record
- (0008,1049)  //Remove Physician Of Record Id Seq
- (0008,1052)  //Remove Performing Physician Id Seq
- (0008,1060)  //Remove Name Of Physician Reading Study
- (0008,1062)  //Remove Physician Reading Study Id Seq
- (0008,1070)  //Remove Operator Name
- (0008,1072)  //Remove Operators Identification Seq
- (0008,1080)  //Remove Admitting Diagnosis Description
- (0008,1084)  //Remove Admitting Diagnosis Code Seq
- (0008,1110)  //Remove Ref Study Seq
- (0008,1111)  //Remove Ref PPS Seq
- (0008,1120)  //Remove Ref Patient Seq
- (0008,1140)  //Remove Ref Image Seq
- (0008,2111)  //Remove Derivation Description
- (0008,2112)  //Remove Source Image Seq
- (0010,0021)  //Remove Issuer Of Patient ID
- (0010,0030)  //Remove Patient Birth Date
//YOB := substring[ (0010,0030), 0, 4] // extract YYYY of the birth date
//(0010,0030) :=  format["{0}0101",YOB] // add 1. January as day and month to YOB
- (0010,0032)  //Remove Patient Birth Time
- (0010,0050)  //Remove Patient Insurance Plan Code Seq
- (0010,0101)  //Remove Patient Primary Language Code Seq
- (0010,0102)  //Remove Patient Primary Language Modifier Code Seq
- (0010,1000)  //Remove Other Patient IDs
- (0010,1001)  //Remove Other Patient Names
- (0010,1002)  //Remove Other Patient IDs Seq
- (0010,1005)  //Remove Patient Birth Name
- (0010,1040)  //Remove Patient Address
- (0010,1050)  //Remove Insurance Plan Identification
- (0010,1060)  //Remove Patient Mother Birth Name
- (0010,1080)  //Remove Military Rank
- (0010,1081)  //Remove Branch Of Service
- (0010,1090)  //Remove Medical Record Locator
- (0010,2000)  //Remove Medical Alerts
- (0010,2110)  //Remove Contrast Allergies
- (0010,2150)  //Remove Country Of Residence
- (0010,2152)  //Remove Region Of Residence
- (0010,2154)  //Remove Patient Phone Numbers
- (0010,2180)  //Remove Occupation
- (0010,21b0)  //Remove Additional Patient History
- (0010,21f0)  //Remove Patient Religious Preference
- (0010,2297)  //Remove Responsible Person
- (0010,2299)  //Remove Responsible Organization
- (0010,4000)  //Remove Patient Comments
- (0018,0010)  //Remove Contrast Bolus Agent
- (0018,1400)  //Remove Acquisition Device Processing Description
- (0018,4000)  //Remove Acquisition Comments
- (0018,9424)  //Remove Acquisition Protocol Description
- (0018,a003)  //Remove Contribution Description
- (0020,0010)  //Remove Study ID
- (0020,3401)  //Remove Modifying Device ID
- (0020,3404)  //Remove Modifying Device Manufacturer
- (0020,3406)  //Remove Modified Image Description
- (0020,4000)  //Remove Image Comments
- (0020,9158)  //Remove Frame Comments
- (0028,4000)  //Remove Image Presentation Comments
- (0032,0012)  //Remove Study ID Issuer
- (0032,0032)  //Remove Study Verified Date
- (0032,0033)  //Remove Study Verified Time
- (0032,0034)  //Remove Study Read Date
- (0032,0035)  //Remove Study Read Time
- (0032,1020)  //Remove Scheduled Study Location
- (0032,1021)  //Remove Scheduled Study Location AET
- (0032,1030)  //Remove Reason for  Study
- (0032,1032)  //Remove Requesting  Physician
- (0032,1033)  //Remove Requesting  Service
- (0032,1060)  //Remove Requested Procedure Description
- (0032,1070)  //Remove Requested Contrast Agent
- (0032,4000)  //Remove Study Comments
- (0038,0004)  //Remove Ref Patient Alias Seq
- (0038,0010)  //Remove Admission ID
- (0038,0011)  //Remove Issuer Of Admission ID
- (0038,001a)  //Remove Scheduled Admission Date
- (0038,001b)  //Remove Scheduled Admission Time
- (0038,001c)  //Remove Scheduled Discharge Date
- (0038,001d)  //Remove Scheduled Discharge Time
- (0038,001e)  //Remove Scheduled Patient Institution Residence
- (0038,0040)  //Remove Discharge Diagnosis Description
- (0038,0050)  //Remove Special Needs
- (0038,0060)  //Remove Service Episode ID
- (0038,0061)  //Remove Issuer Of Service Episode Id
- (0038,0062)  //Remove Service Episode Description
- (0038,0300)  //Remove Current Patient Location
- (0038,0400)  //Remove Patient Institution Residence
- (0038,0500)  //Remove Patient State
- (0038,1234)  //Remove Referenced Patient Alias Seq
- (0038,4000)  //Remove Visit Comments
- (0040,0001)  //Remove Scheduled Station AET
- (0040,0006)  //Remove Scheduled Performing Physician Name
- (0040,0007)  //Remove SPS Description
- (0040,000b)  //Remove Scheduled Performing Physician Identification Sequence
- (0040,0010)  //Remove Scheduled Station Name
- (0040,0011)  //Remove SPS Location
- (0040,0012)  //Remove Pre Medication
- (0040,0241)  //Remove Performed Station AET
- (0040,0242)  //Remove Performed Station Name
- (0040,0243)  //Remove Performed Location
- (0040,0253)  //Remove PPS ID
- (0040,0254)  //Remove PPS Description
- (0040,0275)  //Remove Request Attributes Seq
- (0040,0280)  //Remove PPS Comments
- (0040,0555)  //Remove Acquisition Context Seq
- (0040,1001)  //Remove Requested Procedure ID
- (0040,1004)  //Remove Patient Transport Arrangements
- (0040,1005)  //Remove Requested Procedure Location
- (0040,1010)  //Remove Names Of Intended Recipients Of Results
- (0040,1011)  //Remove Intended Recipients Of Results ID Sequence
- (0040,1101)  //Remove Person Id Code Sequence
- (0040,1102)  //Remove Person Address
- (0040,1103)  //Remove Person Telephone Numbers
- (0040,1400)  //Remove Requested Procedure Comments
- (0040,2001)  //Remove Reason For The Imaging Service Request
- (0040,2004)  //Remove Issue Date Of Imaging Service Request
- (0040,2005)  //Remove Issue Time Of Imaging Service Request
- (0040,2008)  //Remove Order Entered By
- (0040,2009)  //Remove Order Enterer Location
- (0040,2010)  //Remove Order Call back Phone Number
- (0040,2016)  //Remove Placer Order Number
- (0040,2017)  //Remove Filler Order Number
- (0040,2400)  //Remove Imaging Service Request Comments
- (0040,3001)  //Remove Confidentiality Patient Data
- (0040,4025)  //Remove Scheduled Station Name Code Seq
- (0040,4027)  //Remove Scheduled Station Geographic Loc Code Seq
- (0040,4028)  //Remove Performed Station Name Code Seq
- (0040,4030)  //Remove Performed Station Geo Loc Code Seq
- (0040,4034)  //Remove Scheduled Human Performers Seq
- (0040,4035)  //Remove Actual Human Performers Sequence
- (0040,4036)  //Remove Human Performers Organization
- (0040,4037)  //Remove Human Performers Name
- (0040,a027)  //Remove Verifying Organization
- (0040,a030)  //Remove Verification Date Time
- (0040,a032)  //Remove Observation Date Time
- (0040,a073)  //Remove Verifying Observer Seq
- (0040,a075)  //Remove Verifying Observer Name
- (0040,a078)  //Remove Author Observer Sequence
- (0040,a07a)  //Remove Participant Sequence
- (0040,a07c)  //Remove Custodial Organization Seq
- (0040,a088)  //Remove Verifying Observer Identification Code Seq
- (0040,a120)  //Remove Date Time
- (0040,a121)  //Remove Date
- (0040,a122)  //Remove Time
- (0040,a123)  //Remove Person Name
- (0040,a13a)  //Remove Ref Date time
- (0040,a730)  //Remove Content Seq
- (0060,3000)  //Remove Overlay Data
- (0060,4000)  //Remove Overlay Comments
- (0070,0001)  //Remove Graphic Annotation Sequence
- (0070,0084)  //Remove Content Creators Name
- (0070,0086)  //Remove Content Creators Id Code Seq
- (0088,0200)  //Remove Icon Image Sequence
- (0088,0904)  //Remove Topic Title
- (0088,0906)  //Remove Topic Subject
- (0088,0910)  //Remove Topic Author
- (0088,0912)  //Remove Topic Key Words
- (0400,0100)  //Remove Digital Signature UID
- (0400,0402)  //Remove Ref Digital Signature Seq
- (0400,0403)  //Remove Ref SOP Instance MAC Seq
- (0400,0404)  //Remove MAC
- (0400,0550)  //Remove Modified Attributes Sequence
- (0400,0561)  //Remove Original Attributes Sequence
- (2030,0020)  //Remove Text String
- (300e,0008)  //Remove Reviewer Name
- (4000,0010)  //Remove Arbitrary
- (4000,4000)  //Remove Text Comments
- (4008,0042)  //Remove Results ID Issuer
- (4008,0102)  //Remove Interpretation Recorder
- (4008,010a)  //Remove Interpretation Transcriber
- (4008,010b)  //Remove Interpretation Text
- (4008,010c)  //Remove Interpretation Author
- (4008,0111)  //Remove Interpretation Approver Sequence
- (4008,0114)  //Remove Physician Approving Interpretation
- (4008,0115)  //Remove Interpretation Diagnosis Description
- (4008,0118)  //Remove Results Distribution List Seq
- (4008,0119)  //Remove Distribution Name
- (4008,011a)  //Remove Distribution Address
- (4008,0202)  //Remove Interpretation Id Issuer
- (4008,0300)  //Remove Impressions
- (4008,4000)  //Remove Result Comments
- (fffa,fffa)  //Remove Digital Signatures Seq
- (fffc,fffc)  //Remove Data Set Trailing Padding
(0008,1050) := project //Update with XNAT or DQR routed project name
(0010,0010) := subject //Update with XNAT or DQR routed patient name
(0010,0020) := session //Update with XNAT or DQR routed session ID
(0012,0063) := "Performance test"