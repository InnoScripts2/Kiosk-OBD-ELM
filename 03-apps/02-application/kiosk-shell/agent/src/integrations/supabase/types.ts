export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "13.0.5"
  }
  public: {
    Tables: {
      comments: {
        Row: {
          content: string
          created_at: string
          id: string
          parent_id: string | null
          post_id: string
          updated_at: string
          user_id: string
        }
        Insert: {
          content: string
          created_at?: string
          id?: string
          parent_id?: string | null
          post_id: string
          updated_at?: string
          user_id: string
        }
        Update: {
          content?: string
          created_at?: string
          id?: string
          parent_id?: string | null
          post_id?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "comments_parent_id_fkey"
            columns: ["parent_id"]
            isOneToOne: false
            referencedRelation: "comments"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "comments_post_id_fkey"
            columns: ["post_id"]
            isOneToOne: false
            referencedRelation: "posts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "comments_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      device_commands: {
        Row: {
          acknowledged_at: string | null
          attempt: number | null
          command_id: string
          command_type: string
          completed_at: string | null
          created_at: string
          environment: string
          error_message: string | null
          kiosk_id: string | null
          latency_ms: number | null
          mdm_device_id: string | null
          metadata: Json | null
          payload: Json | null
          requested_at: string
          result_payload: Json | null
          source: string | null
          started_at: string | null
          status: string
          updated_at: string
        }
        Insert: {
          acknowledged_at?: string | null
          attempt?: number | null
          command_id: string
          command_type: string
          completed_at?: string | null
          created_at?: string
          environment: string
          error_message?: string | null
          kiosk_id?: string | null
          latency_ms?: number | null
          mdm_device_id?: string | null
          metadata?: Json | null
          payload?: Json | null
          requested_at: string
          result_payload?: Json | null
          source?: string | null
          started_at?: string | null
          status: string
          updated_at?: string
        }
        Update: {
          acknowledged_at?: string | null
          attempt?: number | null
          command_id?: string
          command_type?: string
          completed_at?: string | null
          created_at?: string
          environment?: string
          error_message?: string | null
          kiosk_id?: string | null
          latency_ms?: number | null
          mdm_device_id?: string | null
          metadata?: Json | null
          payload?: Json | null
          requested_at?: string
          result_payload?: Json | null
          source?: string | null
          started_at?: string | null
          status?: string
          updated_at?: string
        }
        Relationships: []
      }
      device_events: {
        Row: {
          command_id: string | null
          command_status: string | null
          command_type: string | null
          created_at: string
          environment: string
          event_id: string
          event_type: string
          kiosk_id: string | null
          mdm_device_id: string | null
          message: string | null
          metadata: Json | null
          payload: Json | null
          recorded_at: string
          severity: string
          source: string | null
        }
        Insert: {
          command_id?: string | null
          command_status?: string | null
          command_type?: string | null
          created_at?: string
          environment: string
          event_id: string
          event_type: string
          kiosk_id?: string | null
          mdm_device_id?: string | null
          message?: string | null
          metadata?: Json | null
          payload?: Json | null
          recorded_at: string
          severity: string
          source?: string | null
        }
        Update: {
          command_id?: string | null
          command_status?: string | null
          command_type?: string | null
          created_at?: string
          environment?: string
          event_id?: string
          event_type?: string
          kiosk_id?: string | null
          mdm_device_id?: string | null
          message?: string | null
          metadata?: Json | null
          payload?: Json | null
          recorded_at?: string
          severity?: string
          source?: string | null
        }
        Relationships: []
      }
      device_status: {
        Row: {
          annotations: Json | null
          app_build: number | null
          app_version: string | null
          battery_is_charging: boolean | null
          battery_percent: number | null
          compliance_state: string | null
          created_at: string
          environment: string
          hardware_manufacturer: string | null
          hardware_model: string | null
          id: string
          kiosk_id: string | null
          last_command_id: string | null
          last_command_status: string | null
          mdm_device_id: string | null
          network_type: string | null
          os_api_level: number | null
          os_version: string | null
          policy_version: string | null
          recorded_at: string
          serial_number: string | null
          status: string | null
          uptime_seconds: number | null
          vpn_active: boolean | null
        }
        Insert: {
          annotations?: Json | null
          app_build?: number | null
          app_version?: string | null
          battery_is_charging?: boolean | null
          battery_percent?: number | null
          compliance_state?: string | null
          created_at?: string
          environment: string
          hardware_manufacturer?: string | null
          hardware_model?: string | null
          id?: string
          kiosk_id?: string | null
          last_command_id?: string | null
          last_command_status?: string | null
          mdm_device_id?: string | null
          network_type?: string | null
          os_api_level?: number | null
          os_version?: string | null
          policy_version?: string | null
          recorded_at: string
          serial_number?: string | null
          status?: string | null
          uptime_seconds?: number | null
          vpn_active?: boolean | null
        }
        Update: {
          annotations?: Json | null
          app_build?: number | null
          app_version?: string | null
          battery_is_charging?: boolean | null
          battery_percent?: number | null
          compliance_state?: string | null
          created_at?: string
          environment?: string
          hardware_manufacturer?: string | null
          hardware_model?: string | null
          id?: string
          kiosk_id?: string | null
          last_command_id?: string | null
          last_command_status?: string | null
          mdm_device_id?: string | null
          network_type?: string | null
          os_api_level?: number | null
          os_version?: string | null
          policy_version?: string | null
          recorded_at?: string
          serial_number?: string | null
          status?: string | null
          uptime_seconds?: number | null
          vpn_active?: boolean | null
        }
        Relationships: []
      }
      diagnostics_logs: {
        Row: {
          category: string
          created_at: string
          device_timestamp_ms: number
          entry_id: string
          environment: string
          kiosk_id: string | null
          message: string
          metadata: Json | null
        }
        Insert: {
          category: string
          created_at?: string
          device_timestamp_ms: number
          entry_id: string
          environment: string
          kiosk_id?: string | null
          message: string
          metadata?: Json | null
        }
        Update: {
          category?: string
          created_at?: string
          device_timestamp_ms?: number
          entry_id?: string
          environment?: string
          kiosk_id?: string | null
          message?: string
          metadata?: Json | null
        }
        Relationships: []
      }
      diagnostics_report_deliveries: {
        Row: {
          attempts: number | null
          channel: string
          created_at: string
          delivery_id: string
          dispatched_at: string | null
          environment: string
          generated_at_ms: number
          kiosk_id: string | null
          last_attempt_at: string | null
          last_error: string | null
          metadata: Json | null
          recipient: string
          report_id: string
          session_id: string
          status: string
          updated_at: string
        }
        Insert: {
          attempts?: number | null
          channel: string
          created_at?: string
          delivery_id: string
          dispatched_at?: string | null
          environment: string
          generated_at_ms: number
          kiosk_id?: string | null
          last_attempt_at?: string | null
          last_error?: string | null
          metadata?: Json | null
          recipient: string
          report_id: string
          session_id: string
          status: string
          updated_at?: string
        }
        Update: {
          attempts?: number | null
          channel?: string
          created_at?: string
          delivery_id?: string
          dispatched_at?: string | null
          environment?: string
          generated_at_ms?: number
          kiosk_id?: string | null
          last_attempt_at?: string | null
          last_error?: string | null
          metadata?: Json | null
          recipient?: string
          report_id?: string
          session_id?: string
          status?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "diagnostics_report_deliveries_report_id_fkey"
            columns: ["report_id"]
            isOneToOne: false
            referencedRelation: "diagnostics_reports"
            referencedColumns: ["report_id"]
          },
        ]
      }
      diagnostics_reports: {
        Row: {
          created_at: string
          environment: string
          generated_at_ms: number
          kiosk_id: string | null
          metadata: Json | null
          report_html: string
          report_id: string
          report_pdf_base64: string
          session_id: string
        }
        Insert: {
          created_at?: string
          environment: string
          generated_at_ms: number
          kiosk_id?: string | null
          metadata?: Json | null
          report_html: string
          report_id: string
          report_pdf_base64: string
          session_id: string
        }
        Update: {
          created_at?: string
          environment?: string
          generated_at_ms?: number
          kiosk_id?: string | null
          metadata?: Json | null
          report_html?: string
          report_id?: string
          report_pdf_base64?: string
          session_id?: string
        }
        Relationships: []
      }
      diagnostics_telemetry: {
        Row: {
          created_at: string
          device_timestamp_ms: number
          environment: string
          event_id: string
          event_type: string
          kiosk_id: string | null
          metadata: Json | null
          session_id: string | null
        }
        Insert: {
          created_at?: string
          device_timestamp_ms: number
          environment: string
          event_id: string
          event_type: string
          kiosk_id?: string | null
          metadata?: Json | null
          session_id?: string | null
        }
        Update: {
          created_at?: string
          device_timestamp_ms?: number
          environment?: string
          event_id?: string
          event_type?: string
          kiosk_id?: string | null
          metadata?: Json | null
          session_id?: string | null
        }
        Relationships: []
      }
      follows: {
        Row: {
          created_at: string
          follower_id: string
          following_id: string
          id: string
        }
        Insert: {
          created_at?: string
          follower_id: string
          following_id: string
          id?: string
        }
        Update: {
          created_at?: string
          follower_id?: string
          following_id?: string
          id?: string
        }
        Relationships: [
          {
            foreignKeyName: "follows_follower_id_fkey"
            columns: ["follower_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "follows_following_id_fkey"
            columns: ["following_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      likes: {
        Row: {
          created_at: string
          id: string
          post_id: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          post_id: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          post_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "likes_post_id_fkey"
            columns: ["post_id"]
            isOneToOne: false
            referencedRelation: "posts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "likes_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      payments_audit: {
        Row: {
          amount_minor: number
          created_at: string
          currency: string
          details: Json | null
          environment: string
          event_type: string
          gateway: string
          id: string
          intent_id: string
          kiosk_id: string | null
          operator_id: string | null
          recorded_at: string
          request_id: string | null
          service_type: string
          session_id: string
          status: string | null
        }
        Insert: {
          amount_minor: number
          created_at?: string
          currency?: string
          details?: Json | null
          environment: string
          event_type: string
          gateway: string
          id?: string
          intent_id: string
          kiosk_id?: string | null
          operator_id?: string | null
          recorded_at: string
          request_id?: string | null
          service_type: string
          session_id: string
          status?: string | null
        }
        Update: {
          amount_minor?: number
          created_at?: string
          currency?: string
          details?: Json | null
          environment?: string
          event_type?: string
          gateway?: string
          id?: string
          intent_id?: string
          kiosk_id?: string | null
          operator_id?: string | null
          recorded_at?: string
          request_id?: string | null
          service_type?: string
          session_id?: string
          status?: string | null
        }
        Relationships: []
      }
      lock_events: {
        Row: {
          action: string
          completed_at: string | null
          created_at: string
          device_type: string
          duration_ms: number | null
          environment: string
          error_code: string | null
          error_message: string | null
          kiosk_id: string | null
          lock_event_id: string
          metadata: Json | null
          requested_at: string
          result: string
          session_id: string | null
        }
        Insert: {
          action: string
          completed_at?: string | null
          created_at?: string
          device_type: string
          duration_ms?: number | null
          environment: string
          error_code?: string | null
          error_message?: string | null
          kiosk_id?: string | null
          lock_event_id?: string
          metadata?: Json | null
          requested_at: string
          result: string
          session_id?: string | null
        }
        Update: {
          action?: string
          completed_at?: string | null
          created_at?: string
          device_type?: string
          duration_ms?: number | null
          environment?: string
          error_code?: string | null
          error_message?: string | null
          kiosk_id?: string | null
          lock_event_id?: string
          metadata?: Json | null
          requested_at?: string
          result?: string
          session_id?: string | null
        }
        Relationships: []
      }
      kiosk_payment_intents: {
        Row: {
          amount_minor: number
          confirmed_at: string | null
          created_at: string
          currency: string
          environment: string
          failure_reason: string | null
          gateway: string
          intent_id: string
          kiosk_id: string | null
          metadata: Json | null
          session_id: string
          status: string
          updated_at: string
          expires_at: string | null
        }
        Insert: {
          amount_minor: number
          confirmed_at?: string | null
          created_at?: string
          currency?: string
          environment: string
          failure_reason?: string | null
          gateway: string
          intent_id: string
          kiosk_id?: string | null
          metadata?: Json | null
          session_id: string
          status: string
          updated_at?: string
          expires_at?: string | null
        }
        Update: {
          amount_minor?: number
          confirmed_at?: string | null
          created_at?: string
          currency?: string
          environment?: string
          failure_reason?: string | null
          gateway?: string
          intent_id?: string
          kiosk_id?: string | null
          metadata?: Json | null
          session_id?: string
          status?: string
          updated_at?: string
          expires_at?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "kiosk_payment_intents_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "kiosk_sessions"
            referencedColumns: ["session_id"]
          },
        ]
      }
      posts: {
        Row: {
          content: string
          created_at: string
          id: string
          published: boolean
          slug: string
          title: string
          updated_at: string
          user_id: string
          view_count: number
        }
        Insert: {
          content: string
          created_at?: string
          id?: string
          published?: boolean
          slug: string
          title: string
          updated_at?: string
          user_id: string
          view_count?: number
        }
        Update: {
          content?: string
          created_at?: string
          id?: string
          published?: boolean
          slug?: string
          title?: string
          updated_at?: string
          user_id?: string
          view_count?: number
        }
        Relationships: [
          {
            foreignKeyName: "posts_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      profiles: {
        Row: {
          avatar_url: string | null
          bio: string | null
          created_at: string
          full_name: string | null
          id: string
          updated_at: string
          username: string
          website: string | null
        }
        Insert: {
          avatar_url?: string | null
          bio?: string | null
          created_at?: string
          full_name?: string | null
          id: string
          updated_at?: string
          username: string
          website?: string | null
        }
        Update: {
          avatar_url?: string | null
          bio?: string | null
          created_at?: string
          full_name?: string | null
          id?: string
          updated_at?: string
          username?: string
          website?: string | null
        }
        Relationships: []
      }
      thickness_report_deliveries: {
        Row: {
          attempts: number | null
          channel: string
          created_at: string
          delivery_id: string
          dispatched_at: string | null
          environment: string
          generated_at_ms: number
          kiosk_id: string | null
          last_attempt_at: string | null
          last_error: string | null
          metadata: Json | null
          recipient: string
          report_id: string
          session_id: string
          status: string
          updated_at: string
        }
        Insert: {
          attempts?: number | null
          channel: string
          created_at?: string
          delivery_id: string
          dispatched_at?: string | null
          environment: string
          generated_at_ms: number
          kiosk_id?: string | null
          last_attempt_at?: string | null
          last_error?: string | null
          metadata?: Json | null
          recipient: string
          report_id: string
          session_id: string
          status: string
          updated_at?: string
        }
        Update: {
          attempts?: number | null
          channel?: string
          created_at?: string
          delivery_id?: string
          dispatched_at?: string | null
          environment?: string
          generated_at_ms?: number
          kiosk_id?: string | null
          last_attempt_at?: string | null
          last_error?: string | null
          metadata?: Json | null
          recipient?: string
          report_id?: string
          session_id?: string
          status?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "thickness_report_deliveries_report_id_fkey"
            columns: ["report_id"]
            isOneToOne: false
            referencedRelation: "thickness_reports"
            referencedColumns: ["report_id"]
          },
        ]
      }
      thickness_reports: {
        Row: {
          created_at: string
          environment: string
          generated_at_ms: number
          kiosk_id: string | null
          metadata: Json | null
          report_html: string
          report_id: string
          report_pdf_base64: string
          session_id: string
        }
        Insert: {
          created_at?: string
          environment: string
          generated_at_ms: number
          kiosk_id?: string | null
          metadata?: Json | null
          report_html: string
          report_id: string
          report_pdf_base64: string
          session_id: string
        }
        Update: {
          created_at?: string
          environment?: string
          generated_at_ms?: number
          kiosk_id?: string | null
          metadata?: Json | null
          report_html?: string
          report_id?: string
          report_pdf_base64?: string
          session_id?: string
        }
        Relationships: []
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      cleanup_old_kiosk_data: {
        Args: { retention_days?: number }
        Returns: {
          deleted_count: number
          table_name: string
        }[]
      }
      dearmor: { Args: { "": string }; Returns: string }
      gen_random_uuid: { Args: never; Returns: string }
      gen_salt: { Args: { "": string }; Returns: string }
      get_delivery_queue_stats: {
        Args: never
        Returns: {
          failed_count: number
          oldest_queued: string
          processing_count: number
          queue_name: string
          queued_count: number
        }[]
      }
      get_device_commands_stats: {
        Args: never
        Returns: {
          avg_latency_ms: number
          count: number
          oldest_command: string
          status: string
        }[]
      }
      get_post_stats: {
        Args: { post_uuid: string }
        Returns: {
          comments_count: number
          likes_count: number
        }[]
      }
      get_user_stats: {
        Args: { user_uuid: string }
        Returns: {
          followers_count: number
          following_count: number
          posts_count: number
        }[]
      }
      pgp_armor_headers: {
        Args: { "": string }
        Returns: Record<string, unknown>[]
      }
      show_limit: { Args: never; Returns: number }
      show_trgm: { Args: { "": string }; Returns: string[] }
      uuid_generate_v1: { Args: never; Returns: string }
      uuid_generate_v1mc: { Args: never; Returns: string }
      uuid_generate_v3: {
        Args: { name: string; namespace: string }
        Returns: string
      }
      uuid_generate_v4: { Args: never; Returns: string }
      uuid_generate_v5: {
        Args: { name: string; namespace: string }
        Returns: string
      }
      uuid_nil: { Args: never; Returns: string }
      uuid_ns_dns: { Args: never; Returns: string }
      uuid_ns_oid: { Args: never; Returns: string }
      uuid_ns_url: { Args: never; Returns: string }
      uuid_ns_x500: { Args: never; Returns: string }
    }
    Enums: {
      [_ in never]: never
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {},
  },
} as const
