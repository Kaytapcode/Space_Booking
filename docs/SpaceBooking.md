## **1\. Project Specifications (Đặc tả Dự án SpaceBooking)**

**SpaceBooking** là ứng dụng web nội bộ quản lý đặt phòng họp và thiết bị dùng chung, giải quyết bài toán phân bổ tài nguyên theo khung giờ mà không xảy ra xung đột lịch đặt.

### **1.1. Phân quyền Người dùng (RBAC — 3 Vai trò)**

* **EMPLOYEE (Nhân viên):** Xem lịch trống toàn công ty, tìm kiếm phòng theo điều kiện, đặt phòng (tối đa 2 giờ/lần; nếu đặt phòng lớn sẽ ở trạng thái chờ duyệt), thực hiện Check-in, Check-out sớm hoặc hủy lịch của cá nhân.  
* **MANAGER (Quản lý):** Có toàn bộ quyền của EMPLOYEE, nhưng được miễn giới hạn thời lượng 2 giờ, tự động được duyệt khi đặt mọi loại phòng, đồng thời có quyền phê duyệt (APPROVE) hoặc từ chối (REJECT) yêu cầu đặt phòng của nhân viên.  
* **ADMIN (Quản trị viên):** Quản lý danh mục phòng họp (thêm, sửa, vô hiệu hóa phòng, cấu hình thiết bị đi kèm) và tra cứu toàn bộ nhật ký kiểm toán (Audit Logs) của hệ thống.

### **1.2. Quy tắc Nghiệp vụ & Vòng đời Trạng thái (Business Rules)**

**Thuật toán kiểm tra trùng khung giờ:** Một yêu cầu đặt phòng mới với khoảng thời gian $[{S}_{new},{E}_{new})$ được xem là xung đột với một lịch đã tồn tại $[{S}_{old},{E}_{old})$ (đang ở trạng thái PENDING, CONFIRMED, hoặc CHECKED\_IN) khi thỏa mãn điều kiện:

$${S}_{new}<{E}_{old}và{E}_{new}>{S}_{old}$$

*   
  *(Hai lịch nối tiếp sát nút nhau như 09:00–10:00 và 10:00–11:00 là hợp lệ và không bị tính là trùng).*  
* **Vòng đời trạng thái của Booking (State Machine):**  
  * PENDING: Khởi tạo khi EMPLOYEE đặt phòng có thuộc tính requires\_approval \= true. Chuyển sang CONFIRMED (khi Manager duyệt) hoặc CANCELLED (khi bị từ chối hoặc người đặt tự hủy).  
  * CONFIRMED: Khởi tạo ngay lập tức khi đặt phòng thường hoặc người đặt là MANAGER. Chuyển sang CHECKED\_IN (khi người dùng xác nhận nhận phòng trong khoảng từ 15 phút trước đến 15 phút sau start\_time), CANCELLED (hủy trước giờ bắt đầu), hoặc NO\_SHOW (tác vụ nền tự động chuyển nếu quá start\_time 15 phút mà chưa check-in).  
  * CHECKED\_IN: Đang sử dụng phòng. Chuyển sang COMPLETED khi hết giờ hoặc khi người dùng bấm trả phòng sớm (hệ thống cập nhật lại end\_time bằng thời điểm trả phòng thực tế để người khác có thể đặt khung giờ dư).

### **1.3. Thiết kế Cơ sở dữ liệu (4 Bảng — PostgreSQL)**

| Tên bảng | Các cột chính & Kiểu dữ liệu | Ràng buộc & Ghi chú kỹ thuật |
| :---- | :---- | :---- |
| **users** | id (UUID), email (VARCHAR), password\_hash (VARCHAR), full\_name (VARCHAR), role (VARCHAR), created\_at (TIMESTAMP) | PK(id), UNIQUE(email). Lưu role dạng enum string (EMPLOYEE, MANAGER, ADMIN). |
| **rooms** | id (UUID), name (VARCHAR), location (VARCHAR), capacity (INT), equipments (VARCHAR), requires\_approval (BOOLEAN), is\_active (BOOLEAN), version (BIGINT) | PK(id), UNIQUE(name). Cột version dùng cho **Optimistic Locking** chống đặt trùng đồng thời. |
| **bookings** | id (UUID), room\_id (UUID), user\_id (UUID), title (VARCHAR), start\_time (TIMESTAMP), end\_time (TIMESTAMP), status (VARCHAR), rejection\_reason (VARCHAR), created\_at (TIMESTAMP) | PK(id), FK(room\_id), FK(user\_id). Đánh Composite Index trên (room\_id, status, start\_time, end\_time) để tối ưu truy vấn lọc trùng. |
| **audit\_logs** | id (UUID), booking\_id (UUID), action (VARCHAR), performed\_by (VARCHAR), details (TEXT), created\_at (TIMESTAMP) | PK(id), FK(booking\_id). Lưu vết mọi thay đổi trạng thái (người thực hiện hoặc SYSTEM\_SCHEDULER). |

### **1.4. Danh sách REST API Endpoints Cốt lõi**

* **Auth:** POST /api/v1/auth/register, POST /api/v1/auth/login (trả về JWT \+ thông tin user).  
* **Rooms:**  
  * GET /api/v1/rooms — Lọc động theo sức chứa, thiết bị và khung giờ trống qua JPA Specification.  
  * POST /api/v1/rooms, PUT /api/v1/rooms/{id}, DELETE /api/v1/rooms/{id} (Soft delete bằng is\_active \= false, chỉ ADMIN).  
* **Bookings:**  
  * GET /api/v1/bookings/daily-schedule?date=YYYY-MM-DD — Lấy lịch đặt trong ngày của tất cả phòng (tích hợp Cache).  
  * GET /api/v1/bookings/my-bookings — Lấy danh sách lịch đặt của tài khoản đang đăng nhập (có phân trang).  
  * POST /api/v1/bookings — Tạo yêu cầu đặt phòng mới.  
  * PATCH /api/v1/bookings/{id}/approve \| reject \| check-in \| checkout \| cancel — Chuyển đổi trạng thái theo State Pattern.  
  * GET /api/v1/bookings/{id}/logs — Xem lịch sử thao tác trên đơn đặt phòng.

### **1.5. Yêu cầu Phi chức năng (Non-Functional Requirements)**

* **An toàn đồng thời (Concurrency):** Xử lý triệt để tình huống nhiều request đặt cùng 1 phòng tại cùng một thời điểm; trả về lỗi chuẩn RFC 7807 (ProblemDetail) với mã HTTP 409 Conflict.  
* **Hiệu năng:** API xem lịch tổng quan trong ngày phản hồi dưới 50ms nhờ cơ chế bộ nhớ đệm; loại bỏ hoàn toàn lỗi N+1 query.  
* **Chất lượng mã nguồn:** Đạt tối thiểu **80% line coverage** trên tầng Service và Domain Logic thông qua Unit Test tự động.  
* **Giao diện Responsive:** Tương thích tốt từ màn hình Desktop (từ 1024px trở lên, hiển thị dạng lưới thời gian Timeline) đến Mobile (dưới 768px, tự động chuyển sang dạng thẻ Card dọc thao tác một chạm).

## **2\. Tổng hợp Công nghệ & Lý do Lựa chọn (Góc nhìn Phỏng vấn)**

Trong các buổi phỏng vấn tại công ty Tier 1, nhà tuyển dụng thường hỏi *"Tại sao bạn chọn công nghệ A thay vì B?"* để đánh giá tư duy kiến trúc (*investigating alternatives and technologies*). Bảng dưới đây tổng hợp các lập luận kỹ thuật bạn có thể sử dụng trực tiếp:

| Nhóm công nghệ | Lựa chọn trong dự án | Phương án thay thế đã cân nhắc | Lý do lựa chọn (Luận điểm trả lời phỏng vấn) |
| :---- | :---- | :---- | :---- |
| **Kiến trúc hệ thống** | **Modular Monolith** (Layered / Clean packaging) | Microservices | Nghiệp vụ đặt phòng đòi hỏi tính nhất quán giao dịch (ACID) chặt chẽ. Với quy mô đội nhỏ và triển khai miễn phí, Modular Monolith loại bỏ độ trễ mạng nội bộ, tránh phức tạp của Distributed Transactions (Saga) nhưng vẫn tách biệt rõ ràng các module theo domain để dễ nâng cấp sau này. |
| **Backend Framework** | **Java 21 \+ Spring Boot 3.3+** | Node.js / Express | Java cung cấp hệ thống kiểu tĩnh (strong typing) mạnh mẽ giúp triển khai các OOP Design Patterns (State, Strategy) tường minh và an toàn lúc biên dịch. Spring Boot 3 hỗ trợ sẵn chuẩn ProblemDetail (RFC 7807\) giúp chuẩn hóa cấu trúc báo lỗi REST API toàn hệ thống. |
| **Xử lý tranh chấp (Locking)** | **Optimistic Locking (@Version)** trên Entity Room | synchronized của Java hoặc Pessimistic Locking | Từ khóa synchronized chỉ khóa trên 1 JVM instance, sẽ thất bại nếu hệ thống chạy nhiều instance. Pessimistic Locking (SELECT FOR UPDATE) khóa cứng dòng dữ liệu gây nghẽn luồng đọc. Vì xác suất 2 người bấm đặt cùng 1 phòng vào cùng 1 mili-giây là thấp (low contention), Optimistic Locking cho thông lượng đọc/ghi cao nhất mà vẫn đảm bảo toàn vẹn dữ liệu. |
| **Caching** | **Caffeine Cache** (In-memory) | Redis | Ở quy mô triển khai single-instance, Redis tiêu tốn thêm tài nguyên server và phát sinh độ trễ kết nối mạng (network round-trip). Caffeine chạy trực tiếp trên Heap của JVM với tốc độ truy xuất tính bằng nano-giây, hỗ trợ sẵn cơ chế hết hạn (TTL) và giới hạn bộ nhớ tối đa (maximumSize) chống tràn RAM. |
| **Xử lý sự kiện nội bộ** | **Spring Application Events** (@EventListener) | Apache Kafka / RabbitMQ | Dùng Event-driven nội bộ (Observer Pattern) giúp tách biệt luồng chính (BookingService) khỏi các tác vụ phụ như ghi AuditLog, giúp code tuân thủ nguyên lý Single Responsibility mà không cần gánh chi phí vận hành hạ tầng Message Broker rời. |
| **Frontend** | **Angular 17+ (Standalone) \+ TailwindCSS** | ReactJS / VueJS | Angular cung cấp bộ công cụ doanh nghiệp đồng bộ ngay từ đầu (TypeScript, Reactive Forms, HTTP Interceptors, Route Guards) rất khớp với tư duy OOP của Backend Java. TailwindCSS giúp xây dựng giao diện Responsive thích ứng nhanh giữa Desktop và Mobile. |
| **Testing & CI** | **JUnit 5 \+ Mockito \+ JaCoCo \+ GitHub Actions** | Kiểm thử thủ công qua Postman | Sử dụng @ParameterizedTest của JUnit 5 cho phép kiểm thử hàng loạt ca biên của thuật toán trùng giờ đặt phòng chỉ trong một hàm test. GitHub Actions tự động chạy toàn bộ test suite và kiểm tra coverage mỗi khi mở Pull Request. |

## **3\. Kế hoạch & Các bước Thực hiện Hoàn chỉnh**

### **Chuẩn bị trước khi bắt đầu (Prerequisites)**

* **Công cụ cài đặt trên máy:** JDK 21 (hoặc 17), Maven 3.9+, Node.js 20 LTS, Angular CLI (npm install \-g @angular/cli), Git, IDE (IntelliJ IDEA cho Backend, VS Code cho Frontend).  
* **Tài khoản Cloud miễn phí:** GitHub (lưu mã nguồn và chạy CI), **Neon.tech** hoặc **Supabase** (tạo sẵn 1 database PostgreSQL miễn phí), **Render.com** (host Backend), và **Vercel** (host Frontend).

**1.Thiết kế Hệ thống & Khởi tạo Repository:** Output: ERD, State Diagram, Git Flow repo.  
Vẽ sơ đồ ERD (4 bảng) và sơ đồ chuyển trạng thái (State Diagram) của Booking, lưu vào thư mục /docs/diagrams. Tạo repository trên GitHub, thiết lập 2 nhánh chính main và develop. Khởi tạo khung dự án Spring Boot (chọn dependencies: Spring Web, Spring Data JPA, Spring Security, Validation, PostgreSQL Driver, Caffeine, Lombok) và dự án Angular (Standalone \+ TailwindCSS).

**2.Xây dựng Tầng Persistence & Xác thực JWT:** Stack: Spring Data JPA, Spring Security, Neon PostgreSQL.  
Tạo 4 Entity và Repository tương ứng, cấu hình kết nối tới PostgreSQL trên Neon/Supabase. Cài đặt Spring Security với JWT Authentication (Access Token), mã hóa mật khẩu bằng BCryptPasswordEncoder, và thiết lập @RestControllerAdvice để trả về mọi ngoại lệ dưới dạng chuẩn ProblemDetail (RFC 7807). Tạo sẵn một class DataSeeder (CommandLineRunner) để tự động nạp 3 tài khoản demo (admin, manager, employee) và 5 phòng họp mẫu khi khởi động.

**3.Cài đặt Domain Core với 4 Design Patterns & Locking:** Patterns: State, Strategy, Specification, Observer.  
Triển khai nghiệp vụ cốt lõi theo hướng đối tượng thuần túy:

> 1. Dùng **Specification Pattern** cho API tìm kiếm phòng đa điều kiện.  
> 2. Dùng **Strategy Pattern** (BookingPolicyStrategy) để tách luật kiểm tra đặt phòng giữa Employee (tối đa 2 giờ, cần duyệt phòng lớn) và Manager (không giới hạn, tự động duyệt).  
> 3. Dùng **State Pattern** (BookingState) quản lý các hành động hợp lệ trên từng trạng thái của lịch đặt.  
> 4. Gắn @Version vào Entity Room và @Transactional trên hàm tạo lịch để ngăn chặn đặt trùng đồng thời. Phát ra BookingStatusChangedEvent (**Observer Pattern**) để tự động ghi nhận vào bảng audit\_logs.

**4.Tối ưu Truy vấn, Caching & Tác vụ nền:** Kỹ thuật: @EntityGraph, Caffeine Cache, @Scheduled.  
Sử dụng @EntityGraph trên các phương thức truy vấn danh sách lịch đặt kèm thông tin phòng và người đặt để triệt tiêu lỗi N+1 query. Cấu hình **Caffeine Cache** (@Cacheable cho API lịch trong ngày và @CacheEvict khi có lịch mới tạo, duyệt hoặc hủy). Viết một cronjob với @Scheduled(fixedRate \= 300000\) chạy mỗi 5 phút để tự động chuyển các lịch quá giờ bắt đầu 15 phút mà chưa check-in sang trạng thái NO\_SHOW.

**5.Phát triển Frontend Angular & Giao diện Responsive:** Stack: Angular 17+, Reactive Forms, TailwindCSS.  
Xây dựng AuthInterceptor (tự động gắn Bearer Token và hiển thị thông báo lỗi từ ProblemDetail) và AuthGuard phân quyền route. Hoàn thiện 3 màn hình chính:

> 1. **Trang Lịch tổng quan (Dashboard):** Dùng CSS Grid hiển thị Timeline theo giờ trên Desktop, và tự động chuyển thành danh sách Card dọc với nút đặt nhanh trên màn hình Mobile (dưới 768px).  
> 2. **Form Đặt phòng:** Dùng Angular Reactive Forms kèm Custom Validator kiểm tra giờ kết thúc phải lớn hơn giờ bắt đầu.  
> 3. **Trang Quản lý Lịch cá nhân & Phê duyệt:** Cho phép nhân viên Check-in/Hủy và Manager duyệt yêu cầu.

**6.Viết Automated Tests & Thiết lập GitHub Actions CI:** Tools: JUnit 5, Mockito, JaCoCo, GitHub Actions.  
Viết Unit Test cho toàn bộ tầng Service, các lớp State, Strategy và dùng @ParameterizedTest để kiểm thử đầy đủ các trường hợp biên của thuật toán trùng giờ đặt phòng. Tích hợp plugin **JaCoCo** vào pom.xml để xuất báo cáo độ phủ mã nguồn (mục tiêu đạt trên 80%). Tạo file .github/workflows/ci.yml để tự động chạy mvn test và build Angular mỗi khi đẩy code lên GitHub.

**7.Deploy Production & Kiểm thử Thực tế:** Nền tảng: Render.com, Vercel.  
Viết một file Dockerfile đa tầng (Multi-stage build) gọn nhẹ cho Spring Boot và deploy lên **Render.com** (kết nối biến môi trường tới Neon PostgreSQL). Deploy ứng dụng Angular lên **Vercel** và cấu hình CORS trên Backend cho phép domain của Vercel truy cập. Kiểm thử toàn bộ luồng từ đăng nhập, đặt phòng đến check-in trên cả trình duyệt máy tính và điện thoại thật.

