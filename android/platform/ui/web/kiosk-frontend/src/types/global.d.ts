/**
 * Shared global type declarations for the kiosk frontend runtime.
 */
import type { SupabaseClient } from '@supabase/supabase-js';
import type { Database } from '@/integrations/supabase/types.generated.ts';

export interface SessionContactInfo {
  thickness: string | null;
  diagnostics: string | null;
}

export interface SessionIds {
  kiosk: string | null;
  thicknessId: string | null;
  obdId: string | null;
}

export type SessionServiceType = 'thickness' | 'diagnostics' | null;

export type SessionLifecycleStatus =
  | 'created'
  | 'in_progress'
  | 'awaiting_payment'
  | 'paid'
  | 'measuring'
  | 'scanning'
  | 'reporting'
  | 'completed'
  | 'failed'
  | 'cancelled'
  | 'expired';

export interface SessionReportFlags {
  thickness: boolean;
  diagnostics: boolean;
}

export interface SessionState {
  contact: SessionContactInfo;
  session: SessionIds;
  reportSent: SessionReportFlags;
  selectedService: SessionServiceType;
  thicknessType: string | null;
  obdMode: string;
  obdMake: string | null;
  stage: string | null;
  status: SessionLifecycleStatus;
  serviceType: SessionServiceType;
}

export interface KioskMetadata {
  kiosk_id: string;
  environment: string;
}

declare global {
  interface Window {
    supabase?: SupabaseClient<Database>;
    supabaseConfig?: {
      url: string;
      anonKey?: string;
    };
    SUPABASE_URL?: string;
    SUPABASE_ANON_KEY?: string;
    __supabaseConfig?: {
      url?: string;
      anonKey?: string;
    };
    __kioskSessionState?: SessionState;
    THICKNESS_SESSION_ID?: string | null;
    OBD_SESSION_ID?: string | null;
    SESSION_ID?: string | null;
    kioskMetadata?: KioskMetadata;
    paywallCancelCallback?: () => void;
  }

  interface ImportMetaEnv {
    readonly VITE_SUPABASE_URL?: string;
    readonly VITE_SUPABASE_ANON_KEY?: string;
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }
}

export {};
