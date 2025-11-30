export type DeploymentEnvironment = 'dev' | 'qa' | 'prod';

export function resolveEnvironment(source?: string | null): DeploymentEnvironment {
  if (!source) {
    return 'dev';
  }
  const normalized = source.trim().toLowerCase();
  if (!normalized) {
    return 'dev';
  }
  switch (normalized) {
    case 'prod':
    case 'production':
      return 'prod';
    case 'qa':
    case 'test':
    case 'testing':
      return 'qa';
    default:
      return 'dev';
  }
}
