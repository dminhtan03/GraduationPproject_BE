# AI LLM Pattern Understanding Test Cases

## Organized from Simple to Complex

### Overview

These test cases validate the complete AI chat workflow across:

- **Intent Detection**: Which fast-path (or LLM fallback) is triggered
- **Language Support**: Vietnamese (vi) vs English (en) detection
- **Parameter Extraction**: Building IDs, time ranges, room codes, etc.
- **Response Quality**: Appropriate reply type and data
- **Performance**: Fast-path execution vs LLM fallback

---

## TIER 1: Simple Single-Intent Queries

### 1.1 Current Availability (No Time Constraint)

#### 1.1.1 Vietnamese - Present Tense

```
Input: "Phòng trống hiện tại?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "phong trong" ✓
  - normalized contains "hien tai" ✓
  - NO explicit time range (10h, 11AM, etc.) ✓
Expected Output:
  - ~20 random AVAILABLE-status rooms
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
  - Time range: NOW to NOW+1h
  - buildingIds: [] (no building specified)
```

#### 1.1.2 Vietnamese - Alternative Phrasing

```
Input: "Có phòng nào trống bây giờ?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - normalized contains "bay gio" ✓
Expected Output:
  - ~20 random AVAILABLE rooms
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
```

#### 1.1.3 Vietnamese - Current/Active Variant

```
Input: "Phòng trống đang?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "phong trong" ✓
  - normalized contains "dang" ✓
Expected Output:
  - ~20 random AVAILABLE rooms
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
```

#### 1.1.4 English - Now

```
Input: "Available rooms now?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: English (en) - contains "available"
Detection Logic:
  - normalized contains "available" ✓
  - normalized contains "now" ✓
Expected Output:
  - ~20 random AVAILABLE rooms
  - Reply: "I found X rooms available now."
```

#### 1.1.5 English - Currently

```
Input: "Any free rooms currently?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "free" ✓
  - normalized contains "currently" ✓
Expected Output:
  - ~20 random AVAILABLE rooms
  - Reply: "I found X rooms available now."
```

#### 1.1.6 English - Right Now

```
Input: "What rooms are available right now?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "available" ✓
  - normalized contains "right now" ✓
Expected Output:
  - ~20 random AVAILABLE rooms
  - Reply: "I found X rooms available now."
```

---

### 1.2 Room Detail Queries

#### 1.2.1 Vietnamese - "Chi tiết" Keyword

```
Input: "Chi tiết phòng V5-020"
Detected Fast-Path: tryFastPathRoomDetail()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "chi tiet" ✓
  - extractLocationCode("V5-020") = "V5-020" ✓
Expected Output:
  - Response Type: RoomDetailResponse (JSON)
  - Reply: "Đây là thông tin của phòng V5-020."
  - No availability suggestions (detail-only response)
```

#### 1.2.2 Vietnamese - "Thông tin" Keyword

```
Input: "Thông tin chi tiết phòng N4-008"
Detected Fast-Path: tryFastPathRoomDetail()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "thong tin chi tiet" ✓
  - extractLocationCode("N4-008") = "N4-008" ✓
Expected Output:
  - Response Type: RoomDetailResponse (JSON)
  - Reply: "Đây là thông tin của phòng N4-008."
```

#### 1.2.3 Vietnamese - "Phòng" Variant

```
Input: "Cho tôi thông tin phòng V4-015"
Detected Fast-Path: tryFastPathRoomDetail()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "thong tin phong" ✓
  - extractLocationCode("V4-015") = "V4-015" ✓
Expected Output:
  - Response Type: RoomDetailResponse (JSON)
  - Reply: "Đây là thông tin của phòng V4-015."
```

#### 1.2.4 English - "Details" Keyword

```
Input: "Room details for V5-020"
Detected Fast-Path: tryFastPathRoomDetail()
Language: English (en)
Detection Logic:
  - normalized contains "room detail" ✓
  - extractLocationCode("V5-020") = "V5-020" ✓
Expected Output:
  - Response Type: RoomDetailResponse (JSON)
  - Reply: "Here is the room information for V5-020."
```

#### 1.2.5 English - "Tell me about" Phrasing

```
Input: "Tell me about room N4-008"
Detected Fast-Path: tryFastPathRoomDetail()
Language: English (en)
Detection Logic:
  - normalized contains "details" (no, so fallback)
  - Falls through to LLM with tools
Expected Output:
  - LLM calls find_room_by_location_code tool
  - Eventually returns: RoomDetailResponse + "Here is the room information for N4-008."
```

#### 1.2.6 English - "Information" Keyword

```
Input: "Information on room V4-015"
Detected Fast-Path: tryFastPathRoomDetail()
Language: English (en)
Detection Logic:
  - normalized contains "information" (does not match fast-path keyword "details")
  - Falls to LLM
Expected Output:
  - LLM handles with tools
```

---

### 1.3 Simple Booking (May Require Time)

#### 1.3.1 Vietnamese - Room Only

```
Input: "Đặt phòng V5-020"
Detected Fast-Path: tryFastPathBooking()
Language: Vietnamese (vi)
Detection Logic:
  - extractLocationCode("V5-020") = "V5-020" ✓
  - extractTimeRange(message) = null ✗ (no time like "10h" or "10-11")
  - parseEnglishTimeWithToday(message) = null ✗ (no AM/PM)
  - Falls to LLM - will ask for time
Expected Output:
  - Falls to LLM fallback
  - LLM instructs user to provide time range
  - Reply: "Vui lòng cung cấp khung giờ để đặt phòng V5-020"
```

#### 1.3.2 English - Book with Location Only

```
Input: "Book room V5-020"
Detected Fast-Path: tryFastPathBooking()
Language: English (en)
Detection Logic:
  - extractLocationCode("V5-020") = "V5-020" ✓
  - NO time range extracted
  - Falls to LLM
Expected Output:
  - Falls to LLM fallback
  - LLM asks for time
```

---

## TIER 2: Intermediate - Multiple Conditions

### 2.1 Availability with Building Filter

#### 2.1.1 Vietnamese - Building Only (Current Time Implied)

```
Input: "Tòa Alpha có phòng trống không?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - normalized contains "hien tai" ✗ (NOT explicitly stated)
  - BUT: "co phong" + no explicit time = CURRENT AVAILABILITY implied
  - resolveBuildingIds(normalized):
    * "toa alpha" normalized to "toa alpha"
    * Token "alpha" matches building name "Alpha"
    * Returns: [buildingId_Alpha]
Expected Output:
  - ~20 random AVAILABLE rooms from building Alpha
  - Time range: NOW to NOW+1h
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
```

#### 2.1.2 Vietnamese - Building with Full Phrase

```
Input: "Tòa nhà Alpha có phòng trống hiện tại?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - normalized contains "hien tai" ✓
  - resolveBuildingIds():
    * Normalized: "toa nha alpha co phong trong hien tai"
    * Token "alpha" extracted and matched
    * Returns: [buildingId_Alpha]
Expected Output:
  - ~20 random AVAILABLE rooms from building Alpha
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
```

#### 2.1.3 Vietnamese - Building Beta Variant

```
Input: "Tòa Beta có phòng?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - No explicit time = current implied
  - buildingMatchesMessage(): "beta" token matches building "Beta"
  - Returns: [buildingId_Beta]
Expected Output:
  - ~20 random AVAILABLE rooms from building Beta
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
```

#### 2.1.4 English - Building Alpha

```
Input: "Available rooms in building Alpha?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "available" ✓
  - contains "now" ✗, BUT "in building" + no time = CURRENT
  - Token "alpha" matches building name
  - Returns: [buildingId_Alpha]
Expected Output:
  - ~20 random AVAILABLE rooms from building Alpha
  - Reply: "I found X rooms available now."
```

#### 2.1.5 English - Building Beta With "Free"

```
Input: "Does building Beta have free rooms now?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "free" ✓
  - normalized contains "now" ✓
  - Token "beta" matched
  - Returns: [buildingId_Beta]
Expected Output:
  - ~20 random AVAILABLE rooms from building Beta
  - Reply: "I found X rooms available now."
```

#### 2.1.6 English - Building Without "Now" (Ambiguous)

```
Input: "Any empty rooms in Alpha?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "empty" ✓
  - contains "now" ✗, contains "currently" ✗, contains "right now" ✗
  - Does NOT trigger current availability (no time indicator)
  - Falls to LLM
Expected Output:
  - Falls to LLM
  - LLM understands context, uses search_random_available_rooms tool
  - Returns ~20 AVAILABLE from Alpha
```

---

### 2.2 Availability with Time (Vietnamese Time Formats)

#### 2.2.1 Vietnamese - "10h" Format Tomorrow

```
Input: "Phòng trống vào 10h ngày mai?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "phong trong" ✓
  - extractTimeRange(message) via TIME_RANGE_PATTERN:
    * Pattern: (\\d{1,2})(?:h|:)(\\d{0,2})?\\s*(den|-|–|to)\\s*(\\d{1,2})(?:h|:)(\\d{0,2})?
    * Does NOT match "10h" (no "den/to" part) → returns null
  - parseSingleHourRange(message):
    * Pattern: \\b(\\d{1,2})h\\b matches "10h"
    * Hour = 10, date = tomorrow
    * Returns: TimeRange(tomorrow 10:00, tomorrow 11:00) ✓
  - resolveBuildingIds(): [] (no building specified)
Expected Output:
  - ~20 random AVAILABLE rooms at tomorrow 10:00-11:00
  - Reply: "Mình tìm được X phòng trống phù hợp."
  - Time range: tomorrow 10:00 to tomorrow 11:00
```

#### 2.2.2 Vietnamese - "11h" Format Today (Implicit)

```
Input: "Ngày mai lúc 11h có phòng trống không?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - parseSingleHourRange() finds "11h"
  - Contains "ngay mai" → date = tomorrow
Expected Output:
  - ~20 random AVAILABLE rooms at tomorrow 11:00-12:00
  - Reply: "Mình tìm được X phòng trống phù hợp."
```

#### 2.2.3 Vietnamese - Building + Time Combination

```
Input: "Tòa Alpha 10h ngày mai có phòng nào?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - parseSingleHourRange() finds "10h"
  - resolveBuildingIds() finds "alpha"
  - Date: tomorrow (ngay mai)
Expected Output:
  - ~20 random AVAILABLE rooms from building Alpha, tomorrow 10:00-11:00
  - Reply: "Mình tìm được X phòng trống phù hợp."
```

#### 2.2.4 Vietnamese - Afternoon Time "2h chiều"

```
Input: "Phòng trống lúc 2h chiều hôm nay?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "phong trong" ✓
  - parseSingleHourRange() finds "2h"
  - "2h chiều" = 14:00 in 24h format (but parser only reads the number)
  - Returns: today 2:00-3:00 (WRONG - should be 14:00)
  - ISSUE: Parser doesn't handle "chiều/sáng" (afternoon/morning) prefix
Expected Output:
  - ~20 rooms at today 2:00-3:00 (or 14:00-15:00 if fixed)
  - Reply: "Mình tìm được X phòng trống phù hợp."
  - TODO: Enhance parser to handle "chiều" (PM), "sáng" (AM)
```

---

### 2.3 Availability with Capacity (RAG Trigger)

#### 2.3.1 Vietnamese - Capacity Requirement

```
Input: "Phòng 20 người có trống không?"
Detected Fast-Path: tryFastPathCurrentAvailability() → FAILS (no time keywords matched properly)
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "phong trong" or "co phong" ✓
  - But LLM availability fast-path might not trigger
  - Falls to LLM
Expected Output:
  - Falls to LLM
  - RAG context: RagRoomResolver extracts capacity=20
  - LLM uses search_random_available_rooms tool with capacity filter
  - Returns ~20 AVAILABLE rooms with capacity ≥ 20
  - Reply from LLM: "Tìm được X phòng phù hợp cho 20 người"
```

#### 2.3.2 English - Capacity Request

```
Input: "Rooms for 20 people?"
Detected Fast-Path: Falls to LLM
Language: English (en)
Detection Logic:
  - "available" + "free" keywords not clearly present
  - Falls to LLM
Expected Output:
  - LLM extracts capacity via RAG
  - Returns ~20 AVAILABLE rooms with capacity ≥ 20
```

#### 2.3.3 English - Capacity with Future Date

```
Input: "I need a room for 15 people tomorrow"
Detected Fast-Path: Falls to LLM
Language: English (en)
Detection Logic:
  - No explicit time like "11AM" or "10h"
  - Falls to LLM
Expected Output:
  - LLM extracts capacity=15, date=tomorrow
  - LLM asks for time or assumes morning
```

---

### 2.4 Building + Time Combination

#### 2.4.1 Vietnamese - Full Specification

```
Input: "Tòa Alpha có những phòng nào trống vào 11h ngày mai?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - "nhung phong nao" → isAvailabilityListQuery() = true
  - parseSingleHourRange() finds "11h"
  - resolveBuildingIds() finds "alpha"
  - Date: tomorrow
Expected Output:
  - ~20 random AVAILABLE rooms from building Alpha, tomorrow 11:00-12:00
  - Reply: "Mình tìm được X phòng trống phù hợp."
  - normalizeAvailabilityResponse() filters to AVAILABLE status only
```

#### 2.4.2 Vietnamese - Building Beta Afternoon

```
Input: "Tòa nhà Beta lúc 10h hôm nay có phòng không?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - parseSingleHourRange() finds "10h"
  - resolveBuildingIds() finds "beta"
  - Date: today (hôm nay)
Expected Output:
  - ~20 random AVAILABLE rooms from building Beta, today 10:00-11:00
  - Reply: "Mình tìm được X phòng trống phù hợp."
```

#### 2.4.3 English - Complete Specification

```
Input: "Available rooms building Alpha at 11AM tomorrow?"
Detected Fast-Path: tryFastPathAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "available" ✓
  - parseEnglishTimeWithToday() finds "11AM":
    * Extracts hour=11, minute=0, ampm="am" → 11:00 in 24h
    * Contains "tomorrow" → date = tomorrow
    * Returns: TimeRange(tomorrow 11:00, tomorrow 12:00) ✓
  - resolveBuildingIds() finds "alpha"
Expected Output:
  - ~20 random AVAILABLE rooms from building Alpha, tomorrow 11:00-12:00
  - Reply: "I found X available rooms."
```

#### 2.4.4 English - "Building ... at ... today"

```
Input: "Building Beta at 10AM today - any rooms free?"
Detected Fast-Path: tryFastPathAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "free" ✓
  - parseEnglishTimeWithToday() finds "10AM":
    * Hour=10, minute=0, ampm="am" → 10:00
    * Contains "today" (implicit from message structure)
    * Returns: TimeRange(today 10:00, today 11:00) ✓
  - resolveBuildingIds() finds "beta"
Expected Output:
  - ~20 random AVAILABLE rooms from building Beta, today 10:00-11:00
  - Reply: "I found X available rooms."
```

---

## TIER 3: Complex - Edge Cases & Language Variations

### 3.1 Building Name Variations

#### 3.1.1 Vietnamese - Multiple Building Name Formats

**Building: "Alpha" (stored in database)**

| Input             | Normalized        | Token Extraction          | Match             | buildingIds |
| ----------------- | ----------------- | ------------------------- | ----------------- | ----------- |
| "Tòa Alpha"       | "toa alpha"       | ["toa", "alpha"]          | "alpha" matches   | ✓           |
| "Tòa nhà Alpha"   | "toa nha alpha"   | ["toa", "nha", "alpha"]   | "alpha" matches   | ✓           |
| "Alpha tòa"       | "alpha toa"       | ["alpha", "toa"]          | "alpha" matches   | ✓           |
| "Tòa nhà Epsilon" | "toa nha epsilon" | ["toa", "nha", "epsilon"] | "epsilon" matches | ✓           |

**Building: "Epsilon" (stored as "Epsilon")**

| Input             | Normalized        | Token Extraction          | Match             | buildingIds |
| ----------------- | ----------------- | ------------------------- | ----------------- | ----------- |
| "Tòa Epsilon"     | "toa epsilon"     | ["toa", "epsilon"]        | "epsilon" matches | ✓           |
| "Tòa nhà Epsilon" | "toa nha epsilon" | ["toa", "nha", "epsilon"] | "epsilon" matches | ✓           |
| "Epsilon tòa"     | "epsilon toa"     | ["epsilon", "toa"]        | "epsilon" matches | ✓           |

**Detection Logic (buildingMatchesMessage):**

```
Filtered Tokens: exclude "toa", "nha", "building", "tac", "nhan", "day", "khu", "house", "block", "tower", "complex", "center", "centre"
Match: normalizedMessage.contains(token)
```

#### 3.1.2 English - Multiple Building Name Formats

| Input              | Normalized         | Match             | buildingIds |
| ------------------ | ------------------ | ----------------- | ----------- |
| "building Alpha"   | "building alpha"   | "alpha" matches   | ✓           |
| "Building Alpha"   | "building alpha"   | "alpha" matches   | ✓           |
| "Alpha building"   | "alpha building"   | "alpha" matches   | ✓           |
| "Building Epsilon" | "building epsilon" | "epsilon" matches | ✓           |

#### 3.1.3 Case Insensitivity

```
Input: "ALPHA" or "alpha" or "AlPhA"
Normalized: normalizeMessage() → .toLowerCase(Locale.ROOT) → "alpha"
Match: Always matches building "Alpha" ✓
```

#### 3.1.4 Building Name NOT in Database (Negative Case)

```
Input: "Tòa XYZ có phòng trống?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Detection Logic:
  - normalized contains "co phong" ✓
  - resolveBuildingIds() finds NO matching building "XYZ"
  - Returns: buildingIds = [] (empty)
Expected Output:
  - searchRandomAvailableRooms([], now, now+1h, 20)
  - Returns ~20 random AVAILABLE from ALL buildings (since buildingIds is empty)
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
  - No error/crash - graceful fallback to all buildings
```

---

### 3.2 Vietnamese Time Format Variations

#### 3.2.1 Hour Formats

| Format   | Input    | Regex Match                                  | Parsed Hour | Result                      |
| -------- | -------- | -------------------------------------------- | ----------- | --------------------------- |
| `Xh`     | "10h"    | `\\b(\\d{1,2})h\\b` → "10"                   | 10          | 10:00-11:00                 |
| `X:MM`   | "10:30"  | TIME_RANGE_PATTERN (if "den" follows)        | N/A         | Fails single hour           |
| `XX giờ` | "11 giờ" | `\\b(\\d{1,2})h\\b` fails (space before "h") | N/A         | Falls to LLM                |
| `XhYY`   | "10h30"  | `\\b(\\d{1,2})h\\b` matches "10"             | 10          | 10:00-11:00 (ignores ":30") |

#### 3.2.2 Time Range Formats (Vietnamese "den/to")

| Format        | Input             | Pattern                           | Parsed      |
| ------------- | ----------------- | --------------------------------- | ----------- |
| `Xh-Yh`       | "10h-11h"         | `(\\d{1,2})h.*?den.*?(\\d{1,2})h` | 10:00-11:00 |
| `Xh denYh`    | "10h den 11h"     | Pattern with "den"                | 10:00-11:00 |
| `Xh–Yh`       | "10h–11h"         | Pattern with "–"                  | 10:00-11:00 |
| `Xh toYh`     | "10h to 11h"      | Pattern with "to"                 | 10:00-11:00 |
| `X:MMdenY:MM` | "10:30 den 11:45" | Pattern extracts minutes          | 10:30-11:45 |

#### 3.2.3 Time with Date Specifiers

| Input          | Date Extract                 | Result                              |
| -------------- | ---------------------------- | ----------------------------------- |
| "10h ngày mai" | contains "ngay mai" → +1 day | tomorrow 10:00-11:00                |
| "10h sang mai" | contains "sang mai" → +1 day | tomorrow 10:00-11:00                |
| "10h hôm nay"  | DEFAULT = today              | today 10:00-11:00                   |
| "11h hôm qua"  | (past tense)                 | today 11:00-11:00 (booking invalid) |

#### 3.2.4 Time with Period (AM/PM Vietnamese Style)

| Input                  | Parsed Hour       | Issue                                   |
| ---------------------- | ----------------- | --------------------------------------- |
| "2h chiều" (afternoon) | 2 (should be 14)  | ⚠️ Parser doesn't handle "chiều" suffix |
| "8h sáng" (morning)    | 8 (correct)       | ⚠️ Parser doesn't normalize "sáng"      |
| "11h đêm" (night/PM)   | 11 (should be 23) | ⚠️ Not supported                        |

**TODO Enhancement:** Add pattern for "chiều" (add 12) and "đêm" (add 12) detection.

---

### 3.3 English Time Format Variations

#### 3.3.1 12-Hour Formats

| Format      | Input        | Pattern Regex                  | Parsed |
| ----------- | ------------ | ------------------------------ | ------ |
| `XPM`       | "11PM"       | `(\\d{1,2})\\s*(am\|pm)`       | 23:00  |
| `X:MMPM`    | "11:30PM"    | Same pattern with minute group | 23:30  |
| `Xam`       | "10am"       | `(\\d{1,2})\\s*(am\|pm)`       | 10:00  |
| `X:MMam`    | "10:45am"    | With minute                    | 10:45  |
| `at XPM`    | "at 10am"    | `at\\s+(\\d{1,2})` optional    | 10:00  |
| `at X:MMPM` | "at 10:45am" | Full format                    | 10:45  |

#### 3.3.2 Time with Date Specifiers (English)

| Input                | Date Extract        | Result               |
| -------------------- | ------------------- | -------------------- |
| "11AM tomorrow"      | contains "tomorrow" | tomorrow 11:00-12:00 |
| "11AM today"         | DEFAULT = today     | today 11:00-12:00    |
| "at 10am"            | (not specified)     | today 10:00-11:00    |
| "3 in the afternoon" | NOT PARSED          | Falls to LLM         |

#### 3.3.3 Edge Cases - AM/PM Logic

| Input  | Hour | AMPM | Converted          |
| ------ | ---- | ---- | ------------------ |
| "12PM" | 12   | pm   | 12:00 (noon) ✓     |
| "12AM" | 12   | am   | 00:00 (midnight) ✓ |
| "1PM"  | 1    | pm   | 13:00 ✓            |
| "11PM" | 11   | pm   | 23:00 ✓            |

**Conversion Logic:**

```java
if ("pm".equals(ampm) && hour != 12) {
    hour += 12;  // 1PM → 13:00
} else if ("am".equals(ampm) && hour == 12) {
    hour = 0;    // 12AM → 00:00
}
```

---

### 3.4 Date Specifications

#### 3.4.1 Vietnamese Date Terms

| Term                   | Parsed                | Result               |
| ---------------------- | --------------------- | -------------------- |
| "ngày mai"             | tomorrow              | +1 day               |
| "sang mai"             | tomorrow              | +1 day               |
| "mai" (within " mai ") | tomorrow              | +1 day               |
| "hôm nay"              | today                 | +0 days              |
| "hôm qua"              | NOT in future parsing | Fails booking (past) |

**Implementation:** TIME_RANGE_PATTERN and parseSingleHourRange() check:

```java
if (normalized.contains("ngay mai") || normalized.contains("sang mai")
        || normalized.contains("tomorrow") || normalized.contains(" mai ")) {
    date = date.plusDays(1);
}
```

#### 3.4.2 English Date Terms

| Term        | Parsed     | Result       |
| ----------- | ---------- | ------------ |
| "tomorrow"  | tomorrow   | +1 day       |
| "today"     | today      | +0 days      |
| "next week" | NOT parsed | Falls to LLM |
| "in 3 days" | NOT parsed | Falls to LLM |

#### 3.4.3 Slash Date Format (extractEnglishDateTime)

| Input        | Pattern                            | Parsed                                        |
| ------------ | ---------------------------------- | --------------------------------------------- |
| "12/25/2024" | `(\\d{1,2})/(\\d{1,2})/(\\d{2,4})` | Month=12, Day=25, Year=2024                   |
| "1/5/25"     | Same                               | Month=1, Day=5, Year=2025 (if <100, add 2000) |
| "invalid"    | N/A                                | Returns null                                  |

---

### 3.5 Complex Merged Queries

#### 3.5.1 Vietnamese - Most Complete Query

```
Input: "Tòa Alpha có những phòng nào trống vào 11h sáng ngày mai?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  Step 1: normalized = "toa alpha co nhung phong nao trong va o 11h sang ngay mai"
  Step 2: isAvailabilityListQuery():
    - asksAvailability: contains "phong" ✓
    - asksList: contains "phong nao" ✓
    - asksTime: matches \\d{1,2}h pattern ✓
    - Result: TRUE
  Step 3: asksAvailability (in fast-path):
    - contains "co phong" ✓
    - contains "phong trong" ✓
  Step 4: extractTimeRange() → parseSingleHourRange():
    - Finds "11h" → hour=11
    - Contains "ngay mai" → date = tomorrow
    - Result: TimeRange(tomorrow 11:00, tomorrow 12:00) ✓
  Step 5: resolveBuildingIds():
    - Token "alpha" matches building
    - Result: [buildingId_Alpha] ✓
Expected Output:
  - searchRandomAvailableRooms([buildingId_Alpha], tomorrow 11:00, tomorrow 12:00, 20)
  - ~20 random AVAILABLE rooms
  - normalizeAvailabilityResponse() filters to AVAILABLE status only, caps at 20
  - Reply: "Mình tìm được X phòng trống phù hợp."
Performance:
  - Fast-path execution: ~50-100ms (no LLM call)
  - All parameters extracted correctly from single message
```

#### 3.5.2 Vietnamese - Afternoon Variant

```
Input: "Tòa nhà Beta 2h chiều hôm nay có phòng không?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - parseSingleHourRange() finds "2h" → hour=2
  - Contains "hom nay" → date = today
  - resolveBuildingIds() finds "beta"
  - NO handling of "chiều" suffix (afternoon)
  - Result: today 2:00-3:00 (should probably be 14:00-15:00)
Expected Output:
  - searchRandomAvailableRooms([buildingId_Beta], today 2:00, today 3:00, 20)
  - ~20 random AVAILABLE rooms
  - Reply: "Mình tìm được X phòng trống phù hợp."
Issues:
  - ⚠️ Time parsing fails for "2h chiều" (afternoon) - returns 2:00 instead of 14:00
  - Would need enhanced regex to handle "chiều/sáng" prefixes
```

#### 3.5.3 English - Complete Specification

```
Input: "Available rooms building Alpha at 11AM tomorrow?"
Detected Fast-Path: tryFastPathAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "available" ✓
  - isAvailabilityListQuery():
    - asksAvailability: contains "available" ✓
    - asksList: contains "rooms" ✓
    - asksTime: matches AM/PM pattern ✓
    - Result: TRUE
  - parseEnglishTimeWithToday():
    - Pattern: `(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)` matches "11AM"
    - hour=11, minute=0, ampm="am" → hour remains 11 (AM)
    - Contains "tomorrow" → date = tomorrow
    - Result: TimeRange(tomorrow 11:00, tomorrow 12:00) ✓
  - resolveBuildingIds() finds "alpha"
Expected Output:
  - searchRandomAvailableRooms([buildingId_Alpha], tomorrow 11:00, tomorrow 12:00, 20)
  - ~20 random AVAILABLE rooms
  - Reply: "I found X available rooms."
Performance:
  - Fast-path execution: ~50-100ms (no LLM call)
```

#### 3.5.4 English - Variant Word Order

```
Input: "Building Beta at 2PM today - any free rooms?"
Detected Fast-Path: tryFastPathAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "free" ✓
  - parseEnglishTimeWithToday():
    - Matches "2PM" → hour=2, ampm="pm" → hour becomes 14
    - Contains "today" → date = today
    - Result: TimeRange(today 14:00, today 15:00) ✓
  - resolveBuildingIds() finds "beta"
Expected Output:
  - searchRandomAvailableRooms([buildingId_Beta], today 14:00, today 15:00, 20)
  - ~20 random AVAILABLE rooms
  - Reply: "I found X available rooms."
```

---

### 3.6 Fallback/Error Cases

#### 3.6.1 Vietnamese - Invalid Time Format

```
Input: "Tòa Alpha 1000 giờ ngày mai?"
Detected Fast-Path: tryFastPathAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" or availability pattern ✓ or ✗ (depends on full message)
  - parseSingleHourRange() tries to parse:
    - Pattern: \\b(\\d{1,2})h\\b
    - Finds "1000" → ✗ No match (needs "h" suffix, and 1000 is 4 digits)
    - Returns null
  - extractTimeRange() also fails
  - Falls to LLM
Expected Output:
  - Falls to LLM fallback
  - LLM rejects invalid time and asks for clarification
  - No crash or exception
Robustness: ✓ Graceful error handling
```

#### 3.6.2 Vietnamese - Non-Existent Building

```
Input: "Tòa XYZ có phòng trống?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language: Vietnamese (vi)
Detection Logic:
  - normalized contains "co phong" ✓
  - resolveBuildingIds():
    - Token "xyz" does NOT match any building name in database
    - Returns: [] (empty list)
Expected Output:
  - searchRandomAvailableRooms([], now, now+1h, 20)
  - Returns ~20 random AVAILABLE from ALL buildings (since buildingIds is empty)
  - Reply: "Mình tìm được X phòng trống hiện tại phù hợp."
  - **Note**: User may expect zero results for non-existent building, but system returns all buildings
  - No crash - graceful fallback to all buildings
Improvement Idea: Could detect unknown building and ask for clarification
```

#### 3.6.3 English - Impossible Time (25:00)

```
Input: "Available rooms at 25:00?"
Detected Fast-Path: tryFastPathAvailability()
Language: English (en)
Detection Logic:
  - normalized contains "available" ✓
  - parseEnglishTimeWithToday():
    - Pattern: `(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)` matches "25:00"
    - But regex allows \\d{1,2} → matches "25"
    - hour=25, minute=0, ampm=null (no am/pm after "00")
    - Attempts to create LocalDateTime with hour=25
    - **CRASH RISK**: LocalDateTime.of(year, month, 25, minute) throws exception
Expected Output:
  - parseEnglishTimeWithToday() might throw exception
  - tryFastPathAvailability() catches nothing (no try-catch)
  - **BUG**: Unhandled exception
Improvement: Add validation for hour range 0-23, return null on invalid
```

#### 3.6.4 English - Non-Existent Building Negative Case

```
Input: "Building Nonexistent rooms?"
Detected Fast-Path: Falls to LLM (no availability keywords clear)
Language: English (en)
Detection Logic:
  - normalized = "building nonexistent rooms"
  - Lacks clear availability keywords ("available", "free", "empty")
  - Falls to LLM
Expected Output:
  - LLM processes with tools
  - search_random_available_rooms tool called
  - buildingIds resolved (likely empty since "nonexistent" not in DB)
  - Returns ~20 random AVAILABLE from all buildings
  - No crash
```

---

### 3.7 Mixed Language (Not Officially Supported)

#### 3.7.1 Vietnamese + English Mix

```
Input: "Tòa Alpha available at 10h?"
Detected Fast-Path: tryFastPathAvailability()
Language Detection: detectLanguage():
  - Regex check for Vietnamese diacriticals (àáạ...đ) → YES (from "Tòa")
  - Returns: "vi"
Detection Logic (with Vietnamese language):
  - normalized = "toa alpha available at 10h"
  - contains "available" + "co phong" (NOT present) → ✗
  - Falls to LLM
Expected Output:
  - Falls to LLM (language switch to English or mixed)
  - LLM parses context, returns AVAILABLE rooms
Robustness: ✓ Graceful fallback
```

#### 3.7.2 English + Vietnamese Mix

```
Input: "Building Alpha có phòng không?"
Detected Fast-Path: tryFastPathCurrentAvailability()
Language Detection: detectLanguage():
  - Regex check for Vietnamese diacriticals (àáạ...đ) → YES (from "phòng")
  - Returns: "vi"
Detection Logic:
  - normalized = "building alpha co phong khong"
  - contains "co phong" ✓
  - No explicit time → CURRENT availability ✓
  - resolveBuildingIds() finds "alpha"
Expected Output:
  - ~20 random AVAILABLE from building Alpha
  - Reply in Vietnamese: "Mình tìm được X phòng trống hiện tại phù hợp."
Robustness: ✓ Vietnamese dominates language detection
```

---

## TIER 4: Advanced - Reservation & Action Queries

### 4.1 Cancellation

#### 4.1.1 Vietnamese - Cancel with Reason

```
Input: "Hủy đặt phòng V5-020 vi toi khong the tham du"
Detected Fast-Path: tryFastPathReservationAction()
Language: Vietnamese (vi)
Detection Logic:
  - detectActionIntent():
    - normalized contains "huy" ✓ → ActionIntent.CANCEL
  - extractLocationCode("V5-020") = "V5-020" ✓
  - extractCancelReason():
    - Pattern: `(ly do|vi)\\s+(.+)$` matches "vi toi khong the tham du"
    - Extracts: "toi khong the tham du" ✓
Expected Output:
  - reservationService.cancelReservationByLocationCode("V5-020", reason, auth)
  - If successful: "Đã hủy đặt phòng V5-020"
  - If no active reservation: "Không tìm thấy phòng để hủy"
Performance: ~50ms (no LLM)
```

#### 4.1.2 English - Cancel Simple

```
Input: "Cancel my booking V5-020"
Detected Fast-Path: tryFastPathReservationAction()
Language: English (en)
Detection Logic:
  - detectActionIntent():
    - normalized contains "cancel" ✓ → ActionIntent.CANCEL
  - extractLocationCode("V5-020") = "V5-020" ✓
  - extractCancelReason(): returns null (no reason provided)
  - Default reason: "Khong the tham du"
Expected Output:
  - reservationService.cancelReservationByLocationCode(...)
  - Reply in English: "Your booking for V5-020 has been cancelled."
```

#### 4.1.3 Missing Location Code (Negative Case)

```
Input: "Hủy đặt phòng bây giờ"
Detected Fast-Path: tryFastPathReservationAction()
Language: Vietnamese (vi)
Detection Logic:
  - detectActionIntent() finds CANCEL ✓
  - extractLocationCode() returns null ✗
  - extractReservationId() returns null ✗
  - Both null → no room/reservation identified
Expected Output:
  - Reply: "Vui lòng cung cấp reservationId hoặc mã phòng (locationCode)."
  - Falls through to LLM if user provides more context
```

---

### 4.2 Return Room (Check Out)

#### 4.2.1 Vietnamese - Return Current Room

```
Input: "Trả phòng bây giờ"
Detected Fast-Path: tryFastPathReservationAction()
Language: Vietnamese (vi)
Detection Logic:
  - detectActionIntent():
    - normalized contains "tra phong" ✓ → ActionIntent.RETURN
  - extractLocationCode() returns null ✗
  - extractReservationId() returns null ✗
  - No location/reservation provided
Expected Output:
  - Reply: "Vui lòng cung cấp reservationId hoặc mã phòng (locationCode)."
  - User must provide which room to return
Alternative: Could infer from user's active reservations (not implemented)
```

#### 4.2.2 English - Return with Room Code

```
Input: "Return room V5-020"
Detected Fast-Path: tryFastPathReservationAction()
Language: English (en)
Detection Logic:
  - detectActionIntent():
    - normalized contains "return" ✓ → ActionIntent.RETURN
  - extractLocationCode("V5-020") = "V5-020" ✓
Expected Output:
  - reservationService.returnRoomByLocationCode("V5-020", auth)
  - If successful: "Room V5-020 has been returned."
  - If no active reservation: "No active reservation for room V5-020."
```

---

### 4.3 Extend Booking

#### 4.3.1 Vietnamese - Extend by Hours

```
Input: "Gia hạn thêm 2 giờ phòng V5-020"
Detected Fast-Path: tryFastPathReservationAction()
Language: Vietnamese (vi)
Detection Logic:
  - detectActionIntent():
    - normalized contains "gia han" ✓ → ActionIntent.EXTEND
  - extractLocationCode("V5-020") = "V5-020" ✓
  - extractExtendHour():
    - Pattern: `(\\d+(?:[.,]\\d+)?)\\s*(gio|h)\\b` matches "2 gio"
    - Extracts: 2.0 ✓
Expected Output:
  - reservationService.extendReservationByLocationCode("V5-020", 2.0, auth)
  - Reply: "Đã gia hạn phòng V5-020 thêm 2 giờ."
```

#### 4.3.2 English - Extend with Fractional Hours

```
Input: "Extend my booking by 1.5 hours"
Detected Fast-Path: tryFastPathReservationAction()
Language: English (en)
Detection Logic:
  - detectActionIntent():
    - normalized contains "extend" ✓ → ActionIntent.EXTEND
  - extractLocationCode() returns null ✗ (no room code provided)
  - extractReservationId() returns null ✗
  - No room/reservation identified
Expected Output:
  - Reply: "Vui lòng cung cấp reservationId hoặc mã phòng (locationCode)."
Alternative: Could infer active reservation (not implemented)
```

#### 4.3.3 Missing Hours (Negative Case)

```
Input: "Gia hạn phòng V5-020"
Detected Fast-Path: tryFastPathReservationAction()
Language: Vietnamese (vi)
Detection Logic:
  - detectActionIntent() finds EXTEND ✓
  - extractLocationCode("V5-020") = "V5-020" ✓
  - extractExtendHour() returns null ✗ (no hour number provided)
Expected Output:
  - Reply: "Vui lòng cho biết số giờ muốn gia hạn."
  - Fast-path returns early without calling service
```

---

## TIER 5: System Resilience

### 5.1 Empty/Invalid Inputs

#### 5.1.1 Empty Message

```
Input: "" (empty string)
Detected Fast-Path: All fast-paths check `!StringUtils.hasText(request.getMessage())`
Language: Default to "vi"
Expected Output:
  - All fast-paths return null (no message to process)
  - Falls to LLM
  - LLM with system prompt asks user to provide a message
  - Reply: "Vui lòng nhập yêu cầu của bạn."
Robustness: ✓ No crash
```

#### 5.1.2 Whitespace Only

```
Input: "   " (spaces/tabs only)
Detected Fast-Path: StringUtils.hasText("   ") → false
Expected Output:
  - Falls to LLM
  - LLM asks for clarification
Robustness: ✓ No crash
```

#### 5.1.3 Random Symbols/Gibberish

```
Input: "???" or "***" or "jdhskhd"
Detected Fast-Path: None match any patterns
Language: detectLanguage() defaults to "en" (no Vietnamese diacriticals, no English keywords)
Expected Output:
  - Falls to LLM
  - LLM returns: "I don't understand. Please describe what you need."
Robustness: ✓ No crash
```

---

### 5.2 Very Long Queries

#### 5.2.1 Long Descriptive Vietnamese Query

```
Input: "Xin chào, tôi muốn tìm một phòng họp trong tòa nhà Alpha vào lúc 10 giờ sáng ngày mai,
         vì tôi cần một phòng có thể chứa được 20 người, có các thiết bị như máy chiếu,
         bảng trắng và đủ không gian để có 5 bàn tròn..."

Detection Logic:
  - normalized extracts key tokens: "phong", "toa alpha", "10", "ngay mai", "20 nguoi"
  - Fast-path: tryFastPathAvailability() triggers:
    - Contains "phong", "toa alpha" ✓
    - parseSingleHourRange() finds "10" ✓
    - resolveBuildingIds() finds "alpha" ✓
  - RAG context would extract: capacity=20, building=Alpha
Expected Output:
  - searchRandomAvailableRooms([buildingId_Alpha], tomorrow 10:00, tomorrow 11:00, 20)
  - ~20 AVAILABLE rooms (first result satisfying capacity)
  - Reply: "Mình tìm được X phòng trống phù hợp."
  - Additional requirements (projector, whiteboard, round tables) → handled by LLM or secondary filtering
Robustness: ✓ Long queries handled gracefully
```

---

### 5.3 Rapid Successive Queries

#### 5.3.1 Multiple Queries in Sequence

```
Sequence:
1. User: "Phòng trống hiện tại?"
   → Fast-path returns 20 suggestions
   → Logged to chat_history (sessionId: "session-123")

2. User: "Tòa Alpha có phòng không?"
   → Fast-path returns 20 suggestions (building filtered)
   → Logged to chat_history (same sessionId)

3. User: "Chi tiết phòng V5-020"
   → Fast-path returns room detail
   → Logged to chat_history

4. User: "Đặt phòng V5-020 10h ngày mai"
   → Fast-path booking attempts reservation
   → If success: logged with reservationCreated=true
   → If failed: logged with error message

Expected Output:
  - All messages logged to tbl_chat_history
  - No data loss
  - Chat history persists for session "session-123"
  - Each response completes in <100ms (fast-paths) or <2s (LLM)
Robustness: ✓ Session management works correctly
```

---

## Test Execution Strategy

For each test case, verify:

1. **Intent Detection**
   - Which fast-path triggered?
   - If fallback to LLM, why?
   - Log: `[AiServiceImpl] Fast-path: {path_name}`

2. **Language Handling**
   - Detected language: Vietnamese (vi) or English (en)?
   - Reply language matches input language?
   - Log: `[AiServiceImpl] Language: {language}`

3. **Parameter Extraction**
   - Building IDs extracted correctly?
   - Time range parsed to correct LocalDateTime?
   - Location code extracted?
   - Log: `buildingIds=[...], timeRange=[...], locationCode={code}`

4. **Response Quality**
   - Is reply type appropriate? (suggestions for availability, JSON detail, etc.)
   - Are suggestions exactly ≤20 AVAILABLE status rooms?
   - No verbose/markdown output?
   - Log: `suggestions.size()={count}, status=AVAILABLE`

5. **Speed**
   - Did fast-path execute (<100ms)?
   - Or fall to LLM (1-3s)?
   - Log execution time: `duration={ms}ms`

6. **Chat History**
   - Message logged to tbl_chat_history?
   - Verify database: `SELECT * FROM tbl_chat_history WHERE sessionId='{sessionId}'`

---

## Known Limitations & TODOs

| Issue                                                                    | Impact                                  | Workaround                                              | Priority |
| ------------------------------------------------------------------------ | --------------------------------------- | ------------------------------------------------------- | -------- | ------ |
| "2h chiều" (afternoon) parsed as 2:00 instead of 14:00                   | Wrong time range for afternoon bookings | Specify in 24h format "14h" or use LLM                  | HIGH     |
| "11 giờ" (space before "h") not matched by `\bXh\b`                      | Parser fails on some Vietnamese formats | Enhance regex to `\b\d{1,2}\s\*(?:h                     | giờ)\b`  | MEDIUM |
| Invalid time like "25:00" causes exception in parseEnglishTimeWithToday  | Crash risk on malformed input           | Add hour range validation 0-23                          | HIGH     |
| Non-existent building returns all rooms (no building filter)             | UX confusion                            | Could detect unknown building and ask for clarification | LOW      |
| Reservation cancellation without room/reservation ID requires user input | UX friction                             | Could infer from user's active reservations             | MEDIUM   |
| No support for "3 in the afternoon" English time format                  | Falls to LLM                            | Enhance English time parsing regex                      | LOW      |

---

## Example Test Execution (Manual)

```bash
# Test Case 1.1.1
POST /api/ai/chat
{
  "message": "Phòng trống hiện tại?",
  "sessionId": "test-session-1"
}

Expected Response:
{
  "sessionId": "test-session-1",
  "reply": "Mình tìm được 20 phòng trống hiện tại phù hợp.",
  "suggestions": [
    { "roomId": "room-1", "locationCode": "V5-020", "capacity": 10, "status": "AVAILABLE", ... },
    { "roomId": "room-2", "locationCode": "N4-008", "capacity": 8, "status": "AVAILABLE", ... },
    ...
  ],
  "reservationCreated": false
}

Verification:
✓ Fast-path triggered: tryFastPathCurrentAvailability()
✓ Language: Vietnamese (vi)
✓ Time range: NOW to NOW+1h
✓ Suggestions count: 20 (or fewer if insufficient rooms)
✓ All suggestions status: AVAILABLE
✓ Response time: <100ms
✓ Chat history: logged to database
```

---

## Next Steps for User

To use this test case suite:

1. **Review each test case** and identify high-priority scenarios for your use case
2. **Run manual tests** via Postman/cURL against `/api/ai/chat` endpoint
3. **Automate with JUnit tests** for regression testing
4. **Monitor production logs** and add new cases based on user queries
5. **Fix known limitations** (especially HIGH priority items)

Would you like me to create:

- [ ] Automated JUnit test cases for these scenarios?
- [ ] Postman collection with pre-configured requests?
- [ ] Fixes for known limitations (chiều/sáng parsing, invalid time validation)?
- [ ] Additional test cases for specific edge cases?
