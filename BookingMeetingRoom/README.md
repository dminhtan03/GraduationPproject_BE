# BookingMeetingRoom - AI Chatbot (ChatbotController)

Tai lieu nay tong hop chi tiet toan bo nhung gi da duoc trien khai cho AI chatbot trong he thong, bao gom:

- Kien truc hybrid Rule-based + GPT.
- Luong xu ly end-to-end tu controller den service.
- Danh sach intent, hanh vi va cac business rule da bo sung.
- Tat ca endpoint trong ChatbotController va bo curl test day du.
- Mau request/response cho cac use case quan trong.

## 1) Pham vi da lam

Da trien khai va nang cap day du cho chatbot endpoint `/api/v1/chatbot/*`:

- Session management:
  - Tao session chat.
  - Xoa session chat.
  - Xem lich su chat (danh sach session cua user).
  - Xem chi tiet tung session.
- NLP/Intent:
  - Hybrid parser: Rule parser + GPT extractor.
  - Ho tro tieng Viet + tieng Anh.
  - Co co che merge context tu cac tin nhan truoc trong cung session.
- Nghiệp vu chatbot:
  - Kiem tra phong trong theo tieu chi user hoi.
  - Goi y phong theo suc chua.
  - Dat phong.
  - Huy dat phong.
  - Gia han dat phong.
  - Xem chi tiet co so vat chat (building/floor/room).
- Reliability:
  - GPT quota/error fallback ve rule parser.
  - Xu ly business error than thien (vi du booking function locked).

## 2) ChatbotController - endpoint hien tai

File: `src/main/java/com/finalProject/BookingMeetingRoom/controller/ai/ChatbotController.java`

### 2.1 POST `/api/v1/chatbot/session`

Muc dich:

- Tao sessionId moi de FE su dung cho hoi thoai.

Response data:

```json
{
  "sessionId": "<uuid>"
}
```

### 2.2 POST `/api/v1/chatbot/message`

Muc dich:

- Endpoint chinh cho chatbot text.
- Nhan message + sessionId va tra ket qua xu ly AI.

Request body:

- `message`: noi dung user chat.
- `sessionId`: co the null o lan dau.

### 2.3 POST `/api/v1/chatbot/voice` (multipart/form-data)

Muc dich:

- Ho tro chatbot qua giong noi.

Tham so:

- `audio` (optional): file audio.
- `transcript` (optional): neu co thi dung truc tiep, uu tien hon `audio`.
- `sessionId` (optional).
- `language` (optional): hint cho STT.

Luong:

- Neu co `transcript` -> dung transcript.
- Neu khong co transcript, co audio -> STT de lay transcript.
- Sau do dua ve chung luong `handleMessage` nhu endpoint text.

### 2.4 DELETE `/api/v1/chatbot/session/{sessionId}`

Muc dich:

- Xoa lich su chat cua mot session.

Response data:

```json
{
  "sessionId": "<sessionId>",
  "deletedMessages": 12
}
```

### 2.5 GET `/api/v1/chatbot/history`

Muc dich:

- Lay danh sach session chat cua user dang dang nhap.

### 2.6 GET `/api/v1/chatbot/history/{sessionId}`

Muc dich:

- Lay chi tiet tung message trong 1 session cua user.

## 3) Kien truc AI/NLP da trien khai

### 3.1 Hybrid parser

- Rule parser: `ChatbotMessageParser`
- GPT parser: `OpenAiChatbotLlmService`
- Merge parser: `ChatbotServiceImpl.mergeRuleWithLlm(...)`

Merge policy tong quat:

- Rule co slot roi thi uu tien giu.
- GPT bo sung slot con thieu.
- Intent co can bang de tranh override nguy hiem.
- Co merge context tu recent user messages trong cung session.

### 3.2 GPT fallback an toan

- Neu GPT disabled/thieu key/timeout/quota -> bo qua GPT va dung rule parser.
- Khong lam vo luong dat phong.

### 3.3 Intent da ho tro

- `CHECK_AVAILABLE_ROOMS_TODAY`
- `SUGGEST_ROOMS_BY_CAPACITY`
- `BOOK_ROOM`
- `CANCEL_RESERVATION`
- `EXTEND_RESERVATION`
- `VIEW_FACILITY_DETAILS`
- `FALLBACK`

## 4) Luong xu ly chi tiet `/chatbot/message`

1. Controller nhan request.
2. Service tao/kiem tra `sessionId`.
3. Lay recent user context (toi da 5 tin nhan USER).
4. Log USER message vao chat history.
5. Parse message bang Rule parser.
6. Parse message bang GPT parser (neu bat).
7. Merge Rule + GPT.
8. Merge voi context tu recent messages.
9. Router theo intent:
   - Availability / Suggest / Booking / Cancel / Extend / Facility detail.
10. Bat business exception dac thu (booking lock) de tra thong diep than thien.
11. Gan `sessionId` vao response.
12. Log BOT response vao chat history.
13. Tra response.

## 5) Cac nang cap nghiep vu da lam

### 5.1 Availability - dung theo khung gio user hoi

Da fix:

- Neu user hoi gio cu the (vi du 11:00 ngay mai), ket qua chi lay phong trong dung trong requested window.
- Khong con tra phong chi vi no trong o gio khac.
- `availableTimeSlots` trong case co gio cu the se hien thi dung requested window (vi du `11:00-12:00`), khong con `11:00-00:00`.
- Ho tro filter theo building (vi du "Toa nha Alpha...").

### 5.2 Booking late-night

Da fix case:

- "Dat phong V5-020 vao 23h hom nay" / "Book room ... at 11:00 PM today"

Truoc day:

- End default co the bi wrap ve `00:00` va fail `end <= start`.

Hien tai:

- Co normalize default end an toan cho gio cuoi ngay, tranh invalid range gia.

### 5.3 Cancel reservation qua chatbot

Da them intent va xu ly:

- Nhan lenh huy bang tieng Viet/Anh.
- Uu tien room code user vua noi trong context session gan nhat neu tin nhan hien tai khong co room code.
- Goi dung business service: `reservationService.cancelReservation(...)`.

### 5.4 Extend reservation qua chatbot

Da them intent va xu ly:

- Nhan lenh them gio/gia han bang tieng Viet/Anh.
- Parse so gio tu message (mac dinh 1 gio neu khong neu ro).
- Uu tien room code theo context session gan nhat.
- Goi dung business service: `reservationService.extendReservation(...)`.
- Response da bo sung khung gio moi sau khi extend thanh cong.

### 5.5 Facility details - tra payload giong RoomDetailResponse

Khi user hoi chi tiet phong:

- Chatbot van co `reply` ngan.
- Dong thoi tra them payload `roomDetail` trong `ChatbotMessageResponse` theo dung format `RoomDetailResponse`:
  - `id`, `locationCode`, `capacity`
  - `amenities`, `images`
  - `score`
  - `currentUserId`, `currentUserName`, `checkInTime`
  - `feedbacks`

### 5.6 Booking function locked - response than thien

Da xu ly:

- Neu backend nem `ResponseCode.BOOKING_FUNCTION_LOCKED`, chatbot khong nem loi thang ra client.
- Chatbot tra response than thien (VI/EN), co kem expected unlock time neu co data.

## 6) Response format cua chatbot

Tat ca endpoint chatbot tra dang chung:

```json
{
  "data": {
    "sessionId": "...",
    "reply": "...",
    "intent": "...",
    "availableRooms": [],
    "alternativeRooms": [],
    "reservation": null,
    "roomDetail": null
  },
  "meta": {
    "code": "200",
    "message": null
  }
}
```

Trong do:

- `availableRooms`: dung cho check availability / suggest.
- `alternativeRooms`: dung khi booking conflict.
- `reservation`: dung khi booking thanh cong.
- `roomDetail`: dung khi intent la `VIEW_FACILITY_DETAILS` va resolve duoc room.

## 7) Curl day du cho ChatbotController

Gia su BE chay local: `http://localhost:8080`

### 7.1 Tao session

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/session"
```

### 7.2 Chat text - hoi phong trong (VI)

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Toa nha Alpha co nhung phong nao trong vao 10h sang ngay mai",
    "sessionId": "<SESSION_ID>"
  }'
```

### 7.3 Chat text - hoi phong trong (EN)

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Which rooms are available in Alpha building at 11:00 AM tomorrow?",
    "sessionId": "<SESSION_ID>"
  }'
```

### 7.4 Dat phong (yeu cau login)

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Dat cho toi phong V5-020 vao 23h hom nay",
    "sessionId": "<SESSION_ID>"
  }'
```

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Book me room V5-020 at 11:00 PM today",
    "sessionId": "<SESSION_ID>"
  }'
```

### 7.5 Huy dat phong (yeu cau login)

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Toi muon huy phong V5-020",
    "sessionId": "<SESSION_ID>"
  }'
```

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Cancel my room V5-020 booking",
    "sessionId": "<SESSION_ID>"
  }'
```

### 7.6 Gia han dat phong (yeu cau login)

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Toi muon them 1 gio",
    "sessionId": "<SESSION_ID>"
  }'
```

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Extend my reservation by 2 hours for room V5-020",
    "sessionId": "<SESSION_ID>"
  }'
```

### 7.7 Xem chi tiet phong (tra roomDetail)

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Chi tiet phong V5-020",
    "sessionId": "<SESSION_ID>"
  }'
```

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show details of room V5-020",
    "sessionId": "<SESSION_ID>"
  }'
```

### 7.8 Voice endpoint bang transcript (khuyen nghi)

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/voice" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -F "transcript=Book room V5-020 at 11 PM today" \
  -F "sessionId=<SESSION_ID>" \
  -F "language=en"
```

### 7.9 Voice endpoint bang audio file

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/voice" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -F "audio=@/path/to/audio.wav" \
  -F "sessionId=<SESSION_ID>" \
  -F "language=vi"
```

### 7.10 Xoa session

```bash
curl -X DELETE "http://localhost:8080/api/v1/chatbot/session/<SESSION_ID>"
```

### 7.11 Lay danh sach lich su session cua user

```bash
curl -X GET "http://localhost:8080/api/v1/chatbot/history" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 7.12 Lay chi tiet 1 session

```bash
curl -X GET "http://localhost:8080/api/v1/chatbot/history/<SESSION_ID>" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

## 8) Cau hinh GPT

Bien moi truong khuyen nghi:

- `AI_LLM_ENABLED=true`
- `AI_LLM_API_KEY=<OPENAI_API_KEY>`
- `AI_LLM_MODEL=gpt-4o-mini`
- `AI_LLM_BASE_URL=https://api.openai.com/v1`
- `AI_LLM_TIMEOUT_MS=10000`
- `AI_LLM_QUOTA_COOLDOWN_MS=600000`

Luu y:

- Co the tat GPT va he thong van chay bang rule parser.
- GPT chi lam intent/slot extraction, khong thay the business validation core.

## 9) Cac truong hop business va thong diep

- `BOOKING_FUNCTION_LOCKED`:
  - Chatbot tra message than thien VI/EN (co the kem unlock time).
- Time range invalid:
  - Da xu ly case gio khuya single-time booking de tranh invalid gia.
- Availability theo gio:
  - Tra dung phong trong theo requested window, khong gom phong ban o gio khac.

## 10) Checklist test nhanh

1. Tao session.
2. Hoi availability theo building + gio cu the.
3. Dat phong buoi khuya (23h hom nay).
4. Gia han dat phong, kiem tra response co khung gio moi.
5. Huy dat phong voi/khong voi room code (kiem tra context session).
6. Hoi chi tiet phong va verify `roomDetail` payload.

## 11) Auto deploy len VPS tu GitHub

Da them workflow GitHub Actions de build JAR va deploy tu dong len VPS moi khi push code vao `tan` hoac `main`:

- Workflow: `.github/workflows/deploy-vps.yml`
- JAR se duoc upload vao `/opt/apps/BookingMeetingRoom/target/`
- Service `bookingmeetingroom` se duoc restart tu dong sau khi upload xong

Ban can tao GitHub Secrets sau:

- `VPS_HOST` = `103.153.68.124`
- `VPS_USER` = `root`
- `VPS_PASSWORD` = mat khau SSH cua VPS

Luu y:

- Workflow nay khong phu thuoc vao `.git` tren VPS.
- Moi lan push source moi len GitHub, GitHub Actions se build JAR moi va day sang VPS.
- Neu ban muon chi deploy mot branch, chi can sua `on.push.branches` trong workflow.

7. Goi history endpoint de verify luu USER/BOT log theo session.
