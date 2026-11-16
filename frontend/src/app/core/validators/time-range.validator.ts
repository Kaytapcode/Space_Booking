import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Group validator for `HH:mm` pairs; zero-padded strings compare correctly as text. */
export function endAfterStartValidator(startKey = 'startTime', endKey = 'endTime'): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const start = group.get(startKey)?.value;
    const end = group.get(endKey)?.value;
    if (!start || !end) return null;
    return end <= start ? { endBeforeStart: true } : null;
  };
}
