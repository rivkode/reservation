# Presentation 계층 코드 예시

---

## Controller

```java
// presentation/controller/OrderController.java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CancelOrderService cancelOrderService;
    private final OrderQueryService orderQueryService;

    public OrderController(CancelOrderService cancelOrderService,
                           OrderQueryService orderQueryService) {
        this.cancelOrderService = cancelOrderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public CancelOrderResponse cancel(@PathVariable String orderId,
                                      @Valid @RequestBody CancelOrderRequest request) {
        cancelOrderService.cancel(new CancelOrderCommand(
            new OrderId(orderId),
            new CancellationReason(request.reason())
        ));
        return new CancelOrderResponse(orderId, "CANCELLED");
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable String orderId) {
        OrderView view = orderQueryService.findById(new OrderId(orderId));
        return OrderResponse.from(view);
    }
}
```

---

## Request / Response DTO

```java
// presentation/dto/CancelOrderRequest.java
public record CancelOrderRequest(
    @NotBlank String reason,
    String detail
) {}
```

```java
// presentation/dto/CancelOrderResponse.java
public record CancelOrderResponse(
    String orderId,
    String status
) {}
```

**핵심**:
- 입력 검증 어노테이션(`@NotBlank`, `@Size`)은 **Presentation DTO에만**
- Domain 객체/Application Command와 구조가 달라도 됨 — 각자 역할

---

## 예외 핸들링

```java
// presentation/exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handle(OrderNotFoundException e) {
        return new ErrorResponse("ORDER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(OrderCannotBeCancelledException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(OrderCannotBeCancelledException e) {
        return new ErrorResponse("ORDER_CANNOT_BE_CANCELLED", e.getMessage());
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handle(OrderAlreadyCancelledException e) {
        return new ErrorResponse("ORDER_ALREADY_CANCELLED", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse("VALIDATION_FAILED", message);
    }
}

public record ErrorResponse(String code, String message) {}
```

**핵심**:
- Domain 예외를 HTTP 상태로 일관되게 매핑
- 내부 스택트레이스/시스템 정보 노출 금지
- 에러 응답 형식은 `ErrorResponse` 로 통일
