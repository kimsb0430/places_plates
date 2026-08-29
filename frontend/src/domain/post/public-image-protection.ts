export interface PreventableImageEvent {
  preventDefault: () => void;
}

export function deterPublicImageTransfer(event: PreventableImageEvent): void {
  event.preventDefault();
}
