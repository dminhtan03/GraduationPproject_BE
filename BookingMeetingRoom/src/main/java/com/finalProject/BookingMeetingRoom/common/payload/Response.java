package com.finalProject.BookingMeetingRoom.common.payload;

import com.dslplatform.json.CompiledJson;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Response base of API.
 *
 * @param <T> response body
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> {
    private T data;
    private Metadata meta = new Metadata();

    @CompiledJson
    public Response(T data, Metadata meta) {
        this.data = data;
        this.meta = meta;
    }

    public static <T> Response<T> ofSucceeded() {
        return ofSucceeded((T) null);
    }

    @SuppressWarnings("unchecked")
    public static <T> Response<T> ofSucceeded(T data) {
        Response<T> response = new Response<>();
        response.data = data;
        response.meta.code = Metadata.OK_CODE;
        return response;
    }

    public static <T> Response<List<T>> ofSucceeded(Page<T> data) {
        Response<List<T>> response = new Response<>();
        response.data = data.getContent();
        response.meta.code = Metadata.OK_CODE;
        response.meta.page = data.getNumber();
        response.meta.size = data.getSize();
        response.meta.pages = data.getTotalPages();
        response.meta.total = data.getTotalElements();
        return response;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Metadata {
        public static final String OK_CODE = "200";
        String code;
        Integer page;
        Integer size;
        Integer pages;
        Long total;
        String message;
        List<FieldViolation> errors;

        @CompiledJson
        public Metadata(String code, Integer page, Integer size, Integer pages, Long total, String message,
                        List<FieldViolation> errors) {
            this.code = code;
            this.page = page;
            this.size = size;
            this.pages = pages;
            this.total = total;
            this.message = message;
            this.errors = errors;
        }
    }
}
