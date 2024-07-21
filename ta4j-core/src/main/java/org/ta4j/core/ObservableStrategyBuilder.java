
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Lukáš Kvídera
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.ta4j.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.indicators.IndicatorContext;

/**
 * @author Lukáš Kvídera
 */
public class ObservableStrategyBuilder {

  private final List<EntrySignalListener> entrySignalListeners = new ArrayList<>(1);
  private final List<ExitSignalListener> exitSignalListeners = new ArrayList<>(1);
  private String name = "ANONYMOUS";
  private Rule entry = Rule.NOOP;
  private Rule exit = Rule.NOOP;
  private IndicatorContext indicatorContext = IndicatorContext.empty();


  public ObservableStrategyBuilder withName(final String name) {
    this.name = name;
    return this;
  }


  public ObservableStrategyBuilder withEntryListener(final EntrySignalListener entrySignalListener) {
    this.entrySignalListeners.add(entrySignalListener);
    return this;
  }


  public ObservableStrategyBuilder withExitListener(final ExitSignalListener exitSignalListener) {
    this.exitSignalListeners.add(exitSignalListener);
    return this;
  }


  public ObservableStrategyBuilder withEntry(final Rule entry) {
    this.entry = entry;
    return this;
  }


  public ObservableStrategyBuilder withExit(final Rule exit) {
    this.exit = exit;
    return this;
  }


  public ObservableStrategyBuilder withIndicatorContext(final IndicatorContext indicatorContext) {
    this.indicatorContext = indicatorContext;
    return this;
  }


  public Strategy build() {
    return new Strategy() {
      private Instant currentTick = Instant.EPOCH;


      @Override
      public String getName() {
        return ObservableStrategyBuilder.this.name;
      }


      @Override
      public Rule getEntryRule() {
        return ObservableStrategyBuilder.this.entry;
      }


      @Override
      public Rule getExitRule() {
        return ObservableStrategyBuilder.this.exit;
      }


      @Override
      public void refresh(final Instant tick) {
        if (tick.isAfter(this.currentTick)) {
          ObservableStrategyBuilder.this.indicatorContext.refresh(tick);
          this.currentTick = tick;

          if (isStable() && getEntryRule().isSatisfied()) {
            ObservableStrategyBuilder.this.entrySignalListeners.forEach(l -> l.onSignal(new Signal(tick, getName())));
          } else if (isStable() && getExitRule().isSatisfied()) {
            ObservableStrategyBuilder.this.exitSignalListeners.forEach(l -> l.onSignal(new Signal(tick, getName())));
          }
        }
      }


      @Override
      public boolean isStable() {
        return ObservableStrategyBuilder.this.indicatorContext.isStable();
      }
    };
  }
}


