# SpaceBooking Design System

Dense internal tool, Vietnamese UI, light theme only. Map every token below to `tailwind.config` `theme.extend`; no values outside this file. Contrast ratios computed (WCAG 2.x), all AA.

## Colour (one primary + neutral)
| Token | Hex | Use / contrast |
|---|---|---|
| primary-700 | #0F766E | buttons, links, now-line (white on it 5.5) |
| primary-800 | #115E59 | hover/active (7.6) |
| primary-50 | #F0FDFA | selected row, free-slot hover (text primary-700 5.2) |
| n-0 / n-50 / n-100 / n-200 / n-300 | #FFFFFF / #F9FAFB / #F3F4F6 / #E5E7EB / #D1D5DB | surface / page / subtle fill / border / input border |
| n-500 / n-600 / n-900 | #6B7280 / #4B5563 / #111827 | muted text (4.8) / secondary (7.6) / text (17) |
| danger-700 / danger-50 | #B91C1C / #FEE2E2 | destructive button (6.5), error text on tint (5.3) |

Status (badge = tint bg + dark text + 8px dot; label and icon always shown, never colour alone):
| Status | bg | text | dot | VI label | Icon |
|---|---|---|---|---|---|
| PENDING amber | #FEF3C7 | #78350F (8.1) | #B45309 | Chờ duyệt | clock |
| CONFIRMED blue | #DBEAFE | #1E3A8A (8.5) | #1D4ED8 | Đã xác nhận | calendar-check |
| CHECKED_IN green | #DCFCE7 | #14532D (8.3) | #15803D | Đã check-in | log-in |
| COMPLETED slate | #E2E8F0 | #1E293B (11.9) | #475569 | Hoàn thành | check |
| CANCELLED gray | #F3F4F6 | #4B5563 (6.9) | #6B7280 | Đã hủy (text line-through in timeline) | x |
| NO_SHOW gray | #F3F4F6 | #4B5563 (6.9) | #6B7280 | Vắng mặt | user-x |

## Type
Inter (variable, one family) from Google Fonts with `subset=latin,latin-ext,vietnamese`, `display=swap`; fallback `system-ui, "Segoe UI", sans-serif`. Times and counts use `tabular-nums`. Sentence case, no all-caps labels. Line length <= 70ch.
Scale (size/line): caption 12/16 (timeline axis only), body 14/20 (default, desktop), body-lg 16/24 (mobile body and all inputs), h3 18/28 600, h2 20/28 600, h1 24/32 600. Weights 400/500/600.

## Spacing, shape, motion
- 4px scale: 1=4, 2=8, 3=12, 4=16, 6=24, 8=32. Page gutter 16 mobile, 24 desktop. Table row 40px desktop; controls 36px desktop, 44px mobile.
- Radius: sm 4 (badge, timeline block), md 6 (input, button), lg 8 (panel, dialog). Nothing above 8.
- Shadow: `sm` only (`0 1px 2px rgb(0 0 0 / .06)`), for dialog and menu. Panels use a 1px n-200 border, no shadow.
- Focus: 2px primary-700 ring with 2px offset on every interactive element, never removed.
- Motion: 150ms ease-out for hover, expand and toast; honour `prefers-reduced-motion` (none).
- Icons: Lucide, inlined SVG (no new dependency), 16px desktop, 20px mobile, `aria-hidden` when beside text; icon-only buttons need `aria-label`.

## Components
App shell (top bar, role-aware nav, user menu), Button (primary/secondary/danger/ghost), Input, Select, Textarea, DatePicker, TimeInput (30-min step), Field (label above, helper, error below), StatusBadge, RoomCard, Timeline, BookingBlock, BookingRow, Pagination, ConfirmDialog (reject needs reason), Toast (live region), Skeleton, EmptyState, ErrorState.

## Screens
1. Login / register: single centred form 360px, no marketing.
2. Dashboard (lịch tổng quan): date switcher (previous, today, next, date field), room filter (capacity, equipment), timeline or cards.
3. Form đặt phòng: room, date, start, end, purpose; end > start validated inline; conflict returns toast plus field hint.
4. Lịch của tôi: table (desktop) or cards (mobile) with Check-in and Hủy per row; tab Phê duyệt for MANAGER with Duyệt and Từ chối.

## Desktop timeline (>= 1024px, CSS Grid)
Container `overflow-x:auto`; grid `grid-template-columns: 160px repeat(26, minmax(40px, 1fr))` (07:00 to 20:00, 30-min columns; read hours from API config if present), first column and header row `position:sticky`. One row (48px) per room: name, capacity, equipment icons. Header shows hour labels every 2 columns (caption, tabular-nums), n-200 vertical hairlines. BookingBlock is a `<button>` placed by `grid-column: start / end`, status tint bg, 3px left border in dot colour, text "09:00-10:30 Tên người đặt" truncated, `title` and accessible name with status label. Free cells are buttons (primary-50 on hover) that open the form prefilled; past cells are disabled n-100. Now-line 2px primary-700 on today. Keyboard: arrow keys move between cells, Enter opens.

## Mobile cards (< 1024px, one column; 768-1023 uses 2-column cards)
Day strip (7 days, 44px targets), then one RoomCard per room: name (h3), capacity and equipment line, free-slot chips (44px, tabular-nums, next 4), full-width primary "Đặt nhanh" button 44px, then that room's bookings as rows with StatusBadge. No horizontal scroll.

## States (every list)
- Loading: Skeleton rows/cards matching final layout, `aria-busy`.
- Empty: icon, one line, one action. Examples: "Chưa có lịch đặt nào. Chọn phòng và khung giờ để đặt." / "Không có phòng phù hợp. Bỏ bớt bộ lọc."
- Error: n-0 panel with danger-700 icon, "Không tải được dữ liệu. Thử lại." plus Retry button. Toasts map ProblemDetail `code` to fixed Vietnamese text, never raw `detail`; no exclamation marks.

## Anti-slop
No gradients, glass, emoji icons, hero blocks, identical-card grids for data, decorative shadows or purple. Status never by colour alone. Touch targets >= 44px mobile. Body text never below 14px.
