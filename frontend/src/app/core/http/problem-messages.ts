import { HttpErrorResponse } from '@angular/common/http';
import type { components } from '../../api/schema';

type ErrorCode = components['schemas']['ErrorCode'];

// Record<ErrorCode, ...> makes the compiler fail when the contract adds a code.
const BY_CODE: Record<ErrorCode, string> = {
  VALIDATION_FAILED: 'Dữ liệu chưa hợp lệ. Kiểm tra lại các trường.',
  START_IN_PAST: 'Giờ bắt đầu đã qua. Chọn giờ khác.',
  DURATION_EXCEEDED: 'Mỗi lượt đặt tối đa 120 phút. Rút ngắn thời gian.',
  UNAUTHENTICATED: 'Phiên đăng nhập đã hết hạn. Đăng nhập lại.',
  INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng. Thử lại.',
  ACCESS_DENIED: 'Bạn không có quyền thực hiện thao tác này.',
  ROOM_NOT_FOUND: 'Không tìm thấy phòng. Chọn phòng khác.',
  BOOKING_NOT_FOUND: 'Không tìm thấy lịch đặt. Tải lại danh sách.',
  EMAIL_ALREADY_EXISTS: 'Email đã được đăng ký. Dùng email khác hoặc đăng nhập.',
  ROOM_NAME_ALREADY_EXISTS: 'Tên phòng đã tồn tại. Đổi tên khác.',
  ROOM_HAS_FUTURE_BOOKINGS: 'Phòng còn lịch đặt sắp tới. Hủy các lịch đó trước.',
  CONCURRENT_UPDATE: 'Dữ liệu vừa được thay đổi. Tải lại rồi thử lại.',
  BOOKING_CONFLICT: 'Khung giờ này đã có người đặt. Chọn giờ khác.',
  INVALID_STATE_TRANSITION: 'Trạng thái lịch đặt không cho phép thao tác này. Tải lại danh sách.',
  BOOKING_ALREADY_STARTED: 'Lịch đặt đã bắt đầu nên không thể thực hiện thao tác này.',
  OUTSIDE_CHECKIN_WINDOW: 'Chỉ check-in từ 15 phút trước đến 15 phút sau giờ bắt đầu.',
  INTERNAL_ERROR: 'Hệ thống đang gặp lỗi. Thử lại sau.',
  REQUEST_NOT_SUPPORTED: 'Yêu cầu không được hỗ trợ. Tải lại trang.',
};

const NETWORK = 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.';
const GENERIC = 'Có lỗi xảy ra. Thử lại sau.';

function byStatus(status: number): string {
  if (status === 0) return NETWORK;
  if (status === 400) return BY_CODE.VALIDATION_FAILED;
  if (status === 401) return BY_CODE.UNAUTHENTICATED;
  if (status === 403) return BY_CODE.ACCESS_DENIED;
  if (status === 404) return 'Không tìm thấy dữ liệu. Tải lại trang.';
  if (status === 409) return BY_CODE.CONCURRENT_UPDATE;
  if (status >= 500) return BY_CODE.INTERNAL_ERROR;
  return GENERIC;
}

/** Never returns the server `detail`; only fixed Vietnamese text. */
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return GENERIC;
  if (error.status === 0) return NETWORK;
  const code = (error.error as { code?: unknown } | null)?.code;
  if (typeof code === 'string' && Object.hasOwn(BY_CODE, code)) {
    return BY_CODE[code as ErrorCode];
  }
  return byStatus(error.status);
}
