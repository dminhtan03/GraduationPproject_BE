# BookingMeetingRoom - AI/Chatbot

Tai lieu nay tong hop day du:

- Tat ca nhung thay doi da duoc thuc hien cho luong AI/Chatbot.
- Luong xu ly AI theo tung man (stage) va endpoint.
- Danh sach API kem curl chi tiet de test nhanh.

## 1) Tong ket nhung gi da duoc lam

### 1.1 Muc tieu da trien khai

- Giu nguyen API va business rule-base hien co.
- Them lop NLP dung GPT de hieu ngon ngu tu nhien tot hon.
- Neu GPT khong cau hinh/loi/timeout, he thong tu fallback ve rule-base, khong vo luong dat phong.

### 1.2 Cac thay doi ky thuat da lam

- Them interface ChatbotLlmService de truu tuong hoa viec parse intent + slot bang LLM.
- Them OpenAiChatbotLlmService:
  - Goi OpenAI Chat Completions endpoint /chat/completions.
  - Yeu cau LLM tra JSON co cau truc: intent, roomCode, date, startTime, endTime, minCapacity.
  - Parse JSON tra ve, map sang ParseResult.
  - Bat loi de fail-safe (khong lam sap service).
- Noi vao ChatbotServiceImpl:
  - Parse rule-base nhu cu.
  - Parse bang GPT (neu enabled va co api-key).
  - Merge rule + GPT theo uu tien an toan:
    - Rule co du lieu thi giu rule.
    - Chi bo sung du lieu thieu tu GPT.
    - Intent chi nhan GPT khi rule dang FALLBACK.
  - Sau do tiep tuc merge context va chay business flow dat phong cu.
- Da compile xac nhan build thanh cong.

### 1.3 Ket qua dat duoc

- Tang kha nang hieu cau noi tu nhien va da ngon ngu.
- Giam su phu thuoc vao regex/rule o lop hieu ngon ngu.
- Khong thay doi hop dong API FE dang dung.

## 2) Tong quan kien truc AI hien tai

He thong co 2 nhom endpoint:

1. Chatbot API: /api/v1/chatbot/\*
2. AI API cu: /api/v1/ai/\*

Trong do:

- /api/v1/chatbot/message la luong hybrid moi: Rule-based + GPT NLP (optional).
- /api/v1/chatbot/voice la luong voice theo transcript hoac audio.
- /api/v1/ai/chat va /api/v1/ai/reserve la luong heuristic cu (van giu nguyen).

Tat ca response deu duoc wrap theo dang:

```json
{
  "data": {},
  "meta": {
    "code": "200",
    "message": null
  }
}
```

## 3) Luong AI di qua nhung man nao

## 3.1 Luong chinh: POST /api/v1/chatbot/message

Man 1 - Nhan request

- Controller nhan message + sessionId.
- Neu sessionId rong -> tao moi.

Man 2 - Nho ngu canh

- Lay toi da 5 message USER gan nhat theo sessionId.
- Chua xu ly ngay, chi dung lam context.

Man 3 - Log USER

- Luu 1 dong chat history voi sender = USER.

Man 4 - NLP layer

- Rule parser parse message hien tai (intent/room/date/time/capacity).
- GPT parser (optional) parse cung message + context:
  - Chi chay khi AI_LLM_ENABLED=true va co AI_LLM_API_KEY.
  - Neu loi/timeout -> bo qua GPT, he thong van chay.

Man 5 - Merge ket qua NLP

- Merge rule + GPT theo uu tien an toan.
- Merge tiep voi context de fill slot con thieu.

Man 6 - Router theo intent

- CHECK_AVAILABLE_ROOMS_TODAY -> tim phong trong.
- SUGGEST_ROOMS_BY_CAPACITY -> goi y phong theo suc chua.
- BOOK_ROOM -> dat theo roomCode hoac auto-pick theo capacity.
- FALLBACK -> tra loi huong dan.

Man 7 - Log BOT + Tra response

- Gan sessionId vao response.
- Log BOT reply vao chat history.
- Tra ve cho FE.

## 3.2 Luong voice: POST /api/v1/chatbot/voice

Man 1 - Nhan multipart form-data: audio/transcript/sessionId/language.

Man 2 - Xac dinh text dau vao:

- Neu co transcript -> dung transcript.
- Neu khong co transcript ma co audio -> goi SpeechToTextService.transcribe(...).

Man 3 - Chuyen thanh ChatbotMessageRequest va tai su dung toan bo luong /chatbot/message.

Luu y:

- Neu backend STT chua cau hinh (NoOpSpeechToTextService), can gui transcript truc tiep.

## 3.3 Luong cu: POST /api/v1/ai/chat

Man 1 - Nhap message + optional start/end/capacity.

Man 2 - Heuristic detect keyword booking/suggest/default.

Man 3 - Neu booking:

- Validate du start/end/capacity.
- Tim candidate room theo capacity.
- Kiem tra available theo khung gio.
- Neu co phong hop le -> tao reservation.

Man 4 - Neu suggest:

- Validate start/end.
- Tim danh sach phong available theo khung gio va capacity.

Man 5 - Log USER/BOT, tra AiChatResponse.

## 3.4 Luong cu: POST /api/v1/ai/reserve

Man 1 - FE gui ReservationRequest day du.

Man 2 - Goi truc tiep ReservationService.reserveRoom(...).

Man 3 - Tra AiChatResponse (co reservationCreated=true neu thanh cong).

## 4) Chi tiet endpoint + curl

Gia su BE chay local tai http://localhost:8080.

### 4.1 Chatbot text

Endpoint:

- POST /api/v1/chatbot/message

Muc dich:

- Chat hoi dap phong hop.
- Kiem tra phong trong.
- Dat phong theo roomCode hoac auto-book theo capacity.

Curl (chua login):

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Today available rooms?",
    "sessionId": null
  }'
```

Curl (can login khi booking):

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Book AL-102 from 14:00 to 15:00 tomorrow",
    "sessionId": "<SESSION_ID_TU_LAN_TRUOC>"
  }'
```

### 4.2 Chatbot voice

Endpoint:

- POST /api/v1/chatbot/voice (multipart/form-data)

Muc dich:

- Voice booking/chat, sau do di chung luong chatbot text.

Curl dung transcript truc tiep (khuyen nghi neu STT backend chua cau hinh):

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/voice" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -F "transcript=Book room V21-024 tomorrow at 6PM to 8PM" \
  -F "sessionId=<SESSION_ID>" \
  -F "language=en"
```

Curl gui audio:

```bash
curl -X POST "http://localhost:8080/api/v1/chatbot/voice" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -F "audio=@/path/to/voice.wav" \
  -F "sessionId=<SESSION_ID>" \
  -F "language=vi"
```

### 4.3 AI chat (heuristic cu)

Endpoint:

- POST /api/v1/ai/chat

Muc dich:

- Luong heuristic chat/suggest/booking theo request structure.

Curl suggest:

```bash
curl -X POST "http://localhost:8080/api/v1/ai/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Goi y phong trong giup minh",
    "sessionId": null,
    "startTime": "2026-04-14T09:00:00",
    "endTime": "2026-04-14T10:00:00",
    "capacity": 8
  }'
```

Curl booking:

```bash
curl -X POST "http://localhost:8080/api/v1/ai/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "message": "Dat phong giup minh",
    "sessionId": null,
    "startTime": "2026-04-14T14:00:00",
    "endTime": "2026-04-14T15:00:00",
    "capacity": 10
  }'
```

### 4.4 AI reserve (heuristic cu)

Endpoint:

- POST /api/v1/ai/reserve

Muc dich:

- Dat phong truc tiep khi FE da co du roomId + khung gio.

Curl:

```bash
curl -X POST "http://localhost:8080/api/v1/ai/reserve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{
    "roomId": "<ROOM_ID>",
    "startTime": "2026-04-14T14:00:00",
    "endTime": "2026-04-14T15:00:00",
    "purpose": "Team sync",
    "note": "Booked via AI"
  }'
```

## 5) Cau hinh GPT cho luong chatbot (optional)

Dat bien moi truong:

- AI_LLM_ENABLED=true
- AI_LLM_API_KEY=<OPENAI_API_KEY>
- AI_LLM_MODEL=gpt-4o-mini
- AI_LLM_BASE_URL=https://api.openai.com/v1
- AI_LLM_TIMEOUT_MS=10000

Luu y quan trong:

- Neu khong bat hoac thieu api-key, he thong tu dong quay lai rule-base.
- Dat phong van thong qua business service hien co, khong giao cho LLM quyet dinh nghiep vu cuoi cung.

## 6) Session va chat history

- Lan dau FE gui sessionId = null.
- Backend sinh sessionId va tra lai trong data.sessionId.
- FE phai gui lai sessionId o cac lan tiep theo de giu ngu canh.
- Moi request chatbot/ai chat se log 2 ban ghi history: USER va BOT.

## 7) Loi thuong gap khi test

- Booking khong kem token -> ACCESS_DENIED.
- Start time trong qua khu -> chatbot tra loi yeu cau chon thoi gian tuong lai.
- End <= start -> chatbot tra loi invalid range.
- Voice gui audio khi STT chua cau hinh -> can gui transcript thay the.

## 8) Khuyên nghi van hanh

- Nen tach ro 2 luong cho FE:
  - Luong thong minh/chinh: /api/v1/chatbot/message.
  - Luong cu tuong thich nguoc: /api/v1/ai/chat.
- Neu san xuat, nen bo api-key vao bien moi truong/secret manager, khong hard-code.
