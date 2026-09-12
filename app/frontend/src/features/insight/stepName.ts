const KIND_PREFIX = /^K:/;

const COMPONENTS = /^([^@:/#]+)(?:@([^:/#]+))?(?::([^/#]+))?(?:\/([^#]+))?(?:#(.+))?$/;

function sentence(words: string): string {
  const trimmed = words.trim();
  if (trimmed.length === 0) {
    return trimmed;
  }
  return trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
}

export interface StepName {
  name: string;
  workType: string;
  namesAnActivity: boolean;
  subprocess: boolean;
}

export function readStepKind(kindId: string): StepName {
  const body = kindId.replace(KIND_PREFIX, '');
  const parts = COMPONENTS.exec(body);

  if (!parts) {
    return { name: body, workType: body, namesAnActivity: false, subprocess: false };
  }

  const [, workType, activity, , conversation] = parts;
  const readableWorkType = sentence((workType ?? body).replaceAll('_', ' ').toLowerCase());

  return {
    name: activity ? sentence(activity.replaceAll('-', ' ')) : readableWorkType,
    workType: readableWorkType,
    namesAnActivity: Boolean(activity),
    subprocess: Boolean(conversation),
  };
}

export function stepName(kindId: string): string {
  return readStepKind(kindId).name;
}
