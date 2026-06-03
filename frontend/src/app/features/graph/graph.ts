import { Component, OnInit, OnDestroy, signal, computed, ElementRef, ViewChild, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GraphService, AnomalyGraphDTO, GraphFilters } from '../../core/services/graph';
import { WebSocketService } from '../../core/services/websocket';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { TerminationOverlay } from '../../shared/termination-overlay/termination-overlay';
import {AlertToasts} from '../../shared/alert-toasts/alert-toasts';

@Component({
  selector: 'app-graph',
  standalone: true,
  imports: [CommonModule, FormsModule, Sidebar, TerminationOverlay, AlertToasts],
  templateUrl: './graph.html'
})
export class GraphPage implements OnInit, OnDestroy {
  @ViewChild('graphContainer', { static: false }) graphContainer!: ElementRef;

  anomalies = signal<AnomalyGraphDTO[]>([]);
  timelines = signal<any[]>([]);
  loading   = signal(false);
  error     = signal('');
  svgWidth  = signal(900);

  filters: GraphFilters = {
    timelineId : null,
    paradoxRisk: null,
    yearFrom   : null,
    yearTo     : null,
  };

  readonly paradoxRiskOptions = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly LANE_HEIGHT   = 32;
  readonly Y_LABEL_WIDTH = 110;
  readonly X_PADDING     = 24;
  readonly HEADER_HEIGHT = 36;
  readonly DOT_RADIUS    = 4;

  hoveredAnomaly: AnomalyGraphDTO | null = null;
  tooltipX = 0;
  tooltipY = 0;

  role: 'agent' | 'supervisor' =
    localStorage.getItem('role')?.toLowerCase() === 'supervisor' ? 'supervisor' : 'agent';

  private resizeObserver?: ResizeObserver;

  constructor(private graphService: GraphService, private wsService: WebSocketService, private zone: NgZone) {}

  ngOnInit() {
    this.graphService.getTimelines().subscribe({
      next: (data) => { this.timelines.set(data); this.loadAnomalies(); }
    });
    if (this.role === 'agent') {
      this.wsService.connect();
    }
  }

  ngAfterViewInit() {
    this.resizeObserver = new ResizeObserver(entries => {
      this.zone.run(() => {
        const w = entries[0]?.contentRect.width;
        if (w) this.svgWidth.set(w);
      });
    });
    this.resizeObserver.observe(this.graphContainer.nativeElement);
  }

  ngOnDestroy() {
    this.resizeObserver?.disconnect();
  }

  loadAnomalies() {
    this.loading.set(true);
    this.error.set('');
    this.hoveredAnomaly = null;
    this.graphService.getAnomalies(this.filters).subscribe({
      next : (data) => { this.anomalies.set(data); this.loading.set(false); },
      error: (err) => {
        if (err.status === 403) {
          this.error.set('No access to this timeline.');
        } else {
          this.error.set('Failed to load anomaly data.');
        }}});
  }

  applyFilters() { this.loadAnomalies(); }
  clearFilters() {
    this.filters = { timelineId: null, paradoxRisk: null, yearFrom: null, yearTo: null };
    this.loadAnomalies();
  }

  visibleTimelines = computed(() => {
    const ids = new Set(this.anomalies().map(a => a.timelineId));
    if (ids.size === 0) return this.timelines();
    return this.timelines().filter(t => ids.has(t.id));
  });

  svgHeight = computed(() =>
    this.HEADER_HEIGHT + this.visibleTimelines().length * this.LANE_HEIGHT + 16
  );

  yearRange = computed(() => {
    const years = this.anomalies().map(a => a.year);
    if (years.length === 0) return { min: -2000, max: 2000 };
    const min = Math.min(...years);
    const max = Math.max(...years);
    const pad = Math.max(20, Math.round((max - min) * 0.04));
    return { min: min - pad, max: max + pad };
  });

  xTicks = computed(() => {
    const { min, max } = this.yearRange();
    const span = max - min;
    const step = span <= 100 ? 10 : span <= 500 ? 50 : span <= 1000 ? 100 : span <= 2000 ? 200 : 500;
    const ticks: number[] = [];
    const start = Math.ceil(min / step) * step;
    for (let y = start; y <= max; y += step) ticks.push(y);
    return ticks;
  });

  xForYear(year: number): number {
    const { min, max } = this.yearRange();
    const usable = this.svgWidth() - this.Y_LABEL_WIDTH - this.X_PADDING;
    return this.Y_LABEL_WIDTH + ((year - min) / (max - min)) * usable;
  }

  yForTimeline(timelineId: number): number {
    const idx = this.visibleTimelines().findIndex(t => t.id === timelineId);
    return this.HEADER_HEIGHT + idx * this.LANE_HEIGHT + this.LANE_HEIGHT / 2;
  }

  dotFill(risk: string): string {
    switch (risk) {
      case 'LOW'     : return '#bbf7d0';
      case 'MEDIUM'  : return '#fef08a';
      case 'HIGH'    : return '#fdba74';
      case 'CRITICAL': return '#f87171';
      default        : return '#bbf7d0';
    }
  }

  dotStroke(risk: string): string {
    switch (risk) {
      case 'LOW'     : return '#16a34a';
      case 'MEDIUM'  : return '#ca8a04';
      case 'HIGH'    : return '#ea580c';
      case 'CRITICAL': return '#dc2626';
      default        : return '#16a34a';
    }
  }

  onDotHover(event: MouseEvent, anomaly: AnomalyGraphDTO) {
    const rect = (event.currentTarget as SVGElement)
      .closest('.graph-wrap')!.getBoundingClientRect();
    this.hoveredAnomaly = anomaly;
    this.tooltipX = event.clientX - rect.left + 12;
    this.tooltipY = event.clientY - rect.top  - 8;
  }

  onDotLeave() { this.hoveredAnomaly = null; }
}
