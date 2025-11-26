import type { Database } from '@/integrations/supabase/types.generated.ts';

/**
 * @deprecated Используйте типы из `@/integrations/supabase/types.generated` напрямую.
 */
export type SupabaseReportsSchema = Database;
export type DiagnosticsReportRow = Database['public']['Tables']['diagnostics_reports']['Row'];
export type ThicknessReportRow = Database['public']['Tables']['thickness_reports']['Row'];
export type DiagnosticsDeliveryRow = Database['public']['Tables']['diagnostics_report_deliveries']['Row'];
export type ThicknessDeliveryRow = Database['public']['Tables']['thickness_report_deliveries']['Row'];
