export interface SupabaseContactChannel {
    type?: 'phone' | 'email' | string | null;
    value: string | null;
}

export interface SupabaseContactChannels {
    thickness?: SupabaseContactChannel | string | null;
    diagnostics?: SupabaseContactChannel | string | null;
    [key: string]: SupabaseContactChannel | string | null | undefined;
}

export interface SupabaseSessionRefs {
    kiosk?: string | null;
    thicknessId?: string | null;
    obdId?: string | null;
}

export interface SupabaseSessionMetadata {
    contact?: {
        thickness?: string | null;
        diagnostics?: string | null;
        [key: string]: string | null | undefined;
    } | null;
    contactChannels?: SupabaseContactChannels | null;
    reportSent?: boolean | null;
    thicknessType?: string | null;
    obdMode?: string | null;
    obdMake?: string | null;
    selectedService?: string | null;
    serviceType?: string | null;
    stage?: string | null;
    sessionRefs?: SupabaseSessionRefs | null;
    [key: string]: unknown;
}

export interface KioskSessionOverview {
    status?: string | null;
    payment_status?: string | null;
    report_status?: string | null;
    dtc_count?: number | null;
    measurement_completed?: number | null;
    measurement_total?: number | null;
    lock_failures?: number | null;
    last_event?: string | null;
    metadata?: SupabaseSessionMetadata | null;
    contact_channels?: SupabaseContactChannels | null;
    [key: string]: unknown;
}
