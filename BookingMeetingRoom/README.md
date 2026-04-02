# BookingMeetingRoom — AI/Chatbot

Tài liệu này mô tả **toàn bộ API liên quan AI/Chatbot**, kèm **request mẫu + response mẫu**, và **luồng đi chi tiết** theo đúng code hiện tại.

## 1) Tổng quan

Hệ thống có 2 nhóm endpoint liên quan “AI”:

1. **Chatbot (rule-based / parsing + business services)**
   - Endpoint: `/api/v1/chatbot/*`
   - Mục tiêu: xử lý hội thoại theo pattern, có khả năng **hỏi lại khi thiếu thông tin**, **gợi ý theo sức chứa**, **tự chọn phòng để đặt theo sức chứa**, và **nhớ ngữ cảnh theo session**.

2. **AI Chat (heuristic + gọi service có sẵn)**
   - Endpoint: `/api/v1/ai/*`
   - Mục tiêu: chat/tư vấn/gợi ý/đặt phòng dựa trên request structure của FE.

Cả 2 nhóm đều hỗ trợ:

- **`sessionId`**: FE gửi lên để group tin nhắn theo 1 phiên chat.
- **Lưu DB**: mỗi request sẽ log **2 bản ghi** vào `tbl_chat_history` (`USER` và `BOT`).

## 2) Chuẩn response chung

Tất cả endpoint trả về wrapper `Response<T>`:

```json
{
  "data": {
    "...": "..."
  },
  "meta": {
    "code": "200",
    "message": null
  }
}
```

Khi lỗi nghiệp vụ (throw `CustomException(ResponseCode.XYZ)`), GlobalExceptionHandler trả:

```json
{
  "data": null,
  "meta": {
    "code": "ROOM_410",
    "message": "Someone has already booked a room during your chosen time slot."
  }
}
```

## 3) Session + lưu lịch sử + nhớ ngữ cảnh

### 3.1 Bảng `tbl_chat_history`

Entity: `Chat_history` (table `tbl_chat_history`). Các trường chính:

- `user_id` (nullable)
- `chatbot_id` (NOT NULL)
- `session_id` (NOT NULL)
- `sender` (`USER`/`BOT`)
- `message` (TEXT)
- `created_at`

### 3.2 `sessionId` dùng thế nào

- Lần chat đầu: FE không cần gửi `sessionId` → BE auto-generate và trả trong `data.sessionId`.
- Các lần tiếp theo: FE gửi lại `sessionId` để cuộc hội thoại được group đúng.

### 3.3 Nhớ ngữ cảnh (context recall)

Trong `/api/v1/chatbot/message`, BE có cơ chế **nhớ ngữ cảnh nhẹ**:

- Trước khi xử lý message hiện tại, BE đọc tối đa **5 message USER gần nhất** theo `sessionId`.
- BE parse các message cũ và **fill các slot còn thiếu** cho message hiện tại (ví dụ: capacity, date, start/end time, roomCode, intent).

Ví dụ multi-turn:

1. USER: `Book a room with a capacity of 20 people`

2. USER (cùng `sessionId`): `Tomorrow at 10AM`

→ BE sẽ ghép capacity=20 (tin 1) + time/date (tin 2) để đặt phòng.

## 4) Tóm tắt API (kèm request/response mẫu)

### 4.1 Chatbot — Text

**POST** `/api/v1/chatbot/message`

Body:

```json
{
  "message": "<user message>",
  "sessionId": null
}
```

Response `data` là `ChatbotMessageResponse` (các field thường dùng):

```json
{
  "sessionId": "<uuid>",
  "reply": "...",
  "intent": "CHECK_AVAILABLE_ROOMS_TODAY | SUGGEST_ROOMS_BY_CAPACITY | BOOK_ROOM | FALLBACK",
  "availableRooms": [],
  "alternativeRooms": [],
  "reservation": null
}
```

Các kiểu message đang hỗ trợ (đã cấu hình trong parser/service):

- Date:
  - `today`, `hôm nay`, `hom nay`
  - `tomorrow`, `tomorow` (typo), `tmr`, `ngày mai`, `ngay mai`
  - ISO date: `yyyy-mm-dd`
  - Slash/Dash date: `d/M/yyyy` (vd `4/4/2026`, cũng hỗ trợ `-`)
- Time:
  - Single: `at 10AM`, `lúc 10h` → mặc định endTime = +1h
  - Range:
    - `from 14:00 to 15:00` / `từ 14h đến 15h`
    - `at 6PM to 8PM` / `lúc 18h đến 20h`
    - Nếu end time thiếu AM/PM (vd `6PM to 8`) thì end sẽ kế thừa AM/PM từ start
- Room code:
  - Format có dấu phân tách `-` hoặc `_`: `AL-102`, `V21-024`, `V5-020`
- Capacity:
  - `capacity of 20 people`, `accommodate 20`, `20 or more people`, `20+ people`, `sức chứa 20 người`
- Intents:
  - `CHECK_AVAILABLE_ROOMS_TODAY`: hỏi phòng trống hôm nay / từ mốc giờ
  - `SUGGEST_ROOMS_BY_CAPACITY`: gợi ý phòng theo sức chứa (không tính khung giờ)
  - `BOOK_ROOM`: đặt theo mã phòng + thời gian; hoặc auto-book theo sức chứa + thời gian
  - `FALLBACK`: không match pattern

Các request phổ biến + response mẫu:

1. Check phòng trống hôm nay

Request:

```json
{ "message": "Today available rooms?", "sessionId": null }
```

Response mẫu:

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Here are rooms that still have free time today:",
    "intent": "CHECK_AVAILABLE_ROOMS_TODAY",
    "availableRooms": [
      {
        "roomId": "R1",
        "roomCode": "AL-102",
        "building": "Alpha",
        "floor": "1",
        "capacity": 8,
        "amenities": ["TV"],
        "imageUrl": "...",
        "availableTimeSlots": ["14:00–15:00", "16:00–17:00"]
      }
    ],
    "alternativeRooms": null,
    "reservation": null
  },
  "meta": { "code": "200" }
}
```

2. Book theo mã phòng + khoảng giờ (đặt đúng giờ user nhập)

Request:

```json
{
  "message": "Book me room V21-024 for tomorrow at 6PM to 8PM",
  "sessionId": null
}
```

Response thành công (mẫu):

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Booked successfully. You have V21-024 from 18:00 to 20:00 tomorrow.",
    "intent": "BOOK_ROOM",
    "availableRooms": null,
    "alternativeRooms": null,
    "reservation": { "...": "..." }
  },
  "meta": { "code": "200" }
}
```

Nếu overlap, chatbot sẽ trả message theo `ResponseCode` + danh sách phòng gợi ý (mẫu):

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Someone has already booked a room during your chosen time slot. (V21-024, 18:00–20:00)",
    "intent": "BOOK_ROOM",
    "alternativeRooms": [
      {
        "roomId": "R2",
        "roomCode": "V21-025",
        "capacity": 20,
        "availableTimeSlots": ["18:00–20:00"]
      }
    ]
  },
  "meta": { "code": "200" }
}
```

Các response lỗi nghiệp vụ thường gặp (mẫu):

- Thiếu đăng nhập khi booking

Nếu user gọi intent booking nhưng `Authentication=null` → BE throw `ACCESS_DENIED`:

```json
{
  "data": null,
  "meta": {
    "code": "ACCESS_DENIED",
    "message": "..."
  }
}
```

- Thời gian không hợp lệ / quá khứ

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "That start time is in the past. Please choose a future time.",
    "intent": "BOOK_ROOM"
  },
  "meta": { "code": "200" }
}
```

- Range time không hợp lệ (end <= start)

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "The time range looks invalid. Please make sure the end time is after the start time.",
    "intent": "BOOK_ROOM"
  },
  "meta": { "code": "200" }
}
```

3. Suggest rooms theo sức chứa

Request:

```json
{
  "message": "Suggest rooms that can accommodate 20 or more people.",
  "sessionId": null
}
```

Response mẫu:

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Here are rooms that can accommodate 20+ people:",
    "intent": "SUGGEST_ROOMS_BY_CAPACITY",
    "availableRooms": [
      {
        "roomId": "R10",
        "roomCode": "AL-020",
        "capacity": 20,
        "availableTimeSlots": []
      },
      {
        "roomId": "R11",
        "roomCode": "AL-025",
        "capacity": 25,
        "availableTimeSlots": []
      }
    ],
    "alternativeRooms": null,
    "reservation": null
  },
  "meta": { "code": "200" }
}
```

4. Auto-book theo sức chứa (không cần roomCode)

Request:

```json
{
  "message": "Book a room with a capacity of 20 people for tomorrow at 10AM.",
  "sessionId": null
}
```

Response mẫu:

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Done — I booked AL-020 at 10:00 tomorrow for 1 hour (capacity 20+).",
    "intent": "BOOK_ROOM",
    "reservation": { "...": "..." }
  },
  "meta": { "code": "200" }
}
```

Gợi ý: để multi-turn (nhớ ngữ cảnh), các message tiếp theo phải gửi đúng `sessionId`.

### 4.2 Chatbot — Voice

**POST** `/api/v1/chatbot/voice` (multipart/form-data)

Form-data:

- `audio`: file (optional)
- `transcript`: string (optional) — nếu có thì dùng trực tiếp
- `sessionId`: string (optional)
- `language`: `en` / `vi` (optional)

Response: giống `/api/v1/chatbot/message`.

### 4.3 AI — Chat

**POST** `/api/v1/ai/chat`

Request mẫu:

```json
{
  "message": "Gợi ý phòng trống giúp mình",
  "sessionId": null,
  "startTime": "2026-03-29T18:00:00",
  "endTime": "2026-03-29T19:00:00",
  "capacity": 8
}
```

Response `data` là `AiChatResponse` (các field thường gặp):

```json
{
  "sessionId": "<uuid>",
  "reply": "...",
  "reservationCreated": false,
  "reservation": null,
  "suggestions": []
}
```

Các case thường gặp + response mẫu:

1. Booking intent nhưng thiếu field (AI cần start/end/capacity)

Request:

```json
{
  "message": "Đặt phòng giúp mình",
  "sessionId": null,
  "startTime": null,
  "endTime": null,
  "capacity": null
}
```

Response mẫu:

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Bạn muốn đặt phòng, vui lòng cung cấp thời gian bắt đầu, thời gian kết thúc và số người tham dự.",
    "reservationCreated": false
  },
  "meta": { "code": "200" }
}
```

2. Suggestion intent thiếu thời gian

Request:

```json
{ "message": "Gợi ý phòng trống", "sessionId": null }
```

Response mẫu:

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Để gợi ý phòng trống, vui lòng cho tôi biết thời gian bắt đầu và kết thúc.",
    "reservationCreated": false
  },
  "meta": { "code": "200" }
}
```

3. Suggestion intent đủ thời gian (có thể kèm capacity)

Response mẫu (rút gọn):

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Tôi đã tìm thấy một số phòng trống phù hợp. Bạn có thể chọn một trong các phòng sau để đặt:",
    "reservationCreated": false,
    "suggestions": [{ "roomId": "R1", "status": "AVAILABLE" }]
  },
  "meta": { "code": "200" }
}
```

4. Booking intent đủ field (AI tự tìm phòng và đặt)

Request:

```json
{
  "message": "Đặt phòng giúp mình",
  "sessionId": null,
  "startTime": "2026-03-29T18:00:00",
  "endTime": "2026-03-29T19:00:00",
  "capacity": 8
}
```

Response mẫu (rút gọn):

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Tôi đã đặt giúp bạn phòng AL-102 từ 2026-03-29T18:00 đến 2026-03-29T19:00.",
    "reservationCreated": true,
    "reservation": { "id": "<RESERVATION_ID>", "...": "..." }
  },
  "meta": { "code": "200" }
}
```

5. Booking intent nhưng không có phòng đủ sức chứa

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Hiện tại không tìm thấy phòng nào đủ sức chứa cho 20 người.",
    "reservationCreated": false
  },
  "meta": { "code": "200" }
}
```

6. Booking intent nhưng không còn phòng trống phù hợp trong khung giờ

```json
{
  "data": {
    "sessionId": "<uuid>",
    "reply": "Xin lỗi, không còn phòng trống phù hợp trong khung giờ bạn yêu cầu.",
    "reservationCreated": false
  },
  "meta": { "code": "200" }
}
```

### 4.4 AI — Reserve

**POST** `/api/v1/ai/reserve`

Request mẫu:

```json
{
  "roomId": "<ROOM_ID>",
  "startTime": "2026-03-29T18:00:00",
  "endTime": "2026-03-29T19:00:00",
  "purpose": "Meeting",
  "note": "Booked via AI"
}
```

Response mẫu:

```json
{
  "data": {
    "reply": "Tôi đã giúp bạn đặt phòng thành công. Mã đặt chỗ: <RESERVATION_ID>",
    "reservationCreated": true,
    "reservation": { "id": "<RESERVATION_ID>", "...": "..." }
  },
  "meta": { "code": "200" }
}
```

Lưu ý: `/api/v1/ai/reserve` hiện trả `AiChatResponse` nhưng không set `sessionId` và không log `tbl_chat_history` (khác với `/api/v1/ai/chat`).

## 5) Flow chi tiết nhất — Chatbot

### 5.1 Các intent hiện có

- `CHECK_AVAILABLE_ROOMS_TODAY`
- `SUGGEST_ROOMS_BY_CAPACITY`
- `BOOK_ROOM`
- `FALLBACK`

### 5.2 Luồng xử lý `/api/v1/chatbot/message`

1. `ChatbotController.message()` nhận request.

2. `ChatbotServiceImpl.handleMessage()`:

- `ensureSessionId()`
- (Context recall) `getRecentMessages(sessionId, sender=USER, limit=5)`
- Load `User` theo `Authentication` (nếu có)
- Log `Chat_history(sender=USER)`
- Parse message hiện tại bằng `ChatbotMessageParser.parse()`
- Merge với ngữ cảnh (fill slot còn thiếu): capacity/date/time/roomCode/intent
- Switch theo intent:
  - `CHECK_AVAILABLE_ROOMS_TODAY` → `handleAvailableRoomsToday()`
  - `SUGGEST_ROOMS_BY_CAPACITY` → `handleSuggestRoomsByCapacity()`
  - `BOOK_ROOM` → `handleBookRoom()`
  - default → `handleFallback()`
- Set `sessionId` vào response
- Log `Chat_history(sender=BOT)` với `reply`

3. Controller wrap `Response.ofSucceeded(data)`.

### 5.3 Luồng check available

`handleAvailableRoomsToday(message, parsed)`:

- Xác định window:
  - Mặc định: từ `now` đến cuối ngày
  - Nếu có mốc giờ trong message (vd “as of 6 PM today”) → start từ giờ đó
- Query phòng hợp lệ: `roomRepository.findAllWithDetails()` + filter:
  - `status != BROKEN`
  - floor/building không deleted
- Load image theo roomIds
- Query overlaps trong window bằng `reservationRepository.findOverlappingReservationsForRooms(...)`
- Compute free ranges → map sang `ChatbotRoomItemResponse(availableTimeSlots)`

### 5.4 Luồng suggest theo sức chứa

`handleSuggestRoomsByCapacity(message, parsed)`:

- Parse `minCapacity`
- Query phòng hợp lệ rồi filter `room.capacity >= minCapacity`
- Trả danh sách qua `availableRooms` (không tính slot thời gian)

### 5.5 Luồng book room

`handleBookRoom(message, parsed, authentication)`:

- Nếu thiếu `time`: hỏi lại time
- Nếu thiếu `roomCode`:
  - Nếu có `minCapacity` + có time/date → **auto-pick room** theo capacity và check overlap rồi reserve
  - Nếu không có `minCapacity` → hỏi lại roomCode
- Nếu có `roomCode`:
  - `roomRepository.findByLocationCodeIgnoreCase(roomCode)`
  - Gọi `reservationService.reserveRoom(request, authentication)`
  - Nếu overlap (CustomException) → trả message theo `ResponseCode` + gợi ý phòng thay thế (`suggestionEngine.suggest(...)`)

## 6) Flow chi tiết — AI (`/api/v1/ai`)

### 6.1 `/api/v1/ai/chat`

1. `AiController.chat()`

2. `AiServiceImpl.chat()` (theo code hiện tại):

- `sessionId = ensureSessionId(request.sessionId)`
- `messageLower = request.message.toLowerCase()`
- Load `User` theo authentication (nếu có)
- Log `Chat_history(sender=USER)`
- Detect theo keyword:
  - Booking intent nếu message chứa: `đặt phòng/dat phong/book/reserve/đặt lịch/dat lich`
    - Nếu thiếu `startTime/endTime/capacity` → trả reply yêu cầu cung cấp đủ
    - Nếu đủ field → tìm phòng có `capacity >= request.capacity`
      - Với mỗi phòng candidate: gọi `roomService.searchRooms(floorId, start, end)` để check AVAILABLE
      - Khi tìm thấy phòng AVAILABLE đầu tiên → tạo `ReservationRequest(roomId,start,end)` và gọi `reservationService.reserveRoom()`
      - Trả `reservationCreated=true` + `reservation`
  - Suggestion intent nếu message chứa: `gợi ý/goi y/phòng trống/phong trong/suggest`
    - Nếu thiếu `startTime/endTime` → trả reply yêu cầu cung cấp thời gian
    - Nếu đủ time → quét danh sách phòng, filter theo `capacity` (nếu request có)
      - Với mỗi phòng: gọi `roomService.searchRooms(...)` để lấy room AVAILABLE
      - Trả `suggestions` là list `RoomSearchResponse`
  - Default: trả message hướng dẫn cung cấp time/capacity/amenities
- Log `Chat_history(sender=BOT)`

### 6.2 `/api/v1/ai/reserve`

- `AiController.reserveViaAi()` gọi `AiServiceImpl.reserveViaAi()`
- `ReservationService.reserveRoom(request, authentication)`

## 7) Ghi chú & giới hạn

- Context recall là dạng “nhớ ngữ cảnh nhẹ” dựa trên các message USER gần nhất; không phải LLM.
- Chưa có API public để query chat history theo `sessionId` (hiện chỉ dùng nội bộ để merge ngữ cảnh). Nếu cần có thể bổ sung `GET /api/v1/chatbot/history?sessionId=...`.
