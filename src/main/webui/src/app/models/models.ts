// Core data models matching Java backend DTOs

export interface AudioDevice {
  name: string;
  id: string;
  volume: number;
  muted: boolean;
  output: boolean;
  input: boolean;
}

export interface AudioSession {
  pid: number;
  title: string;
  volume: number;
  muted: boolean;
  icon: string | null;
}
