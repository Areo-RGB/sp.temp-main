export type Session = {
  status: 'waiting' | 'running' | 'finished';
  createdAt: number;
  startTime?: number;
  sensitivity?: number;
};

export type ClientData = {
  joinedAt: number;
  elapsedTime?: number;
  deviceName?: string;
};
