const IN_WORDS: Record<string, string> = {
  'gate:job_boundary': 'A job boundary, not work',
  'gate:no_text': 'Nothing was written',
  'gate:no_work_type': 'No kind of work recorded',
  'gate:text_no_signal': 'Too little to go on',
  'gate:lapsed': 'The work lapsed',
  'gate:dropped': 'Somebody dropped it',
  'gate:unknown': 'It ended in a way nothing recorded',
  'gate:direction:query': 'A question, not work',
  'gate:direction:answered': 'An answer, not work',
  'gate:intent:negated': 'Work being declined',
  'gate:intent:meta': 'About the process, not the work',
  'gate:intent:question': 'A question',
  'veto:text_floor': 'The words did not agree',
  below_floor: 'Nothing fitted well enough',
  low_confidence: 'Too uncertain to say',
  no_eligible_template: 'No template was eligible',
};

export function stuckReasonInWords(reason: string): string {
  return IN_WORDS[reason] ?? reason;
}
