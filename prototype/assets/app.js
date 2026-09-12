/**
 * GeoReminder Prototype Interactive Application Logic
 * OpenStreetMap + Leaflet + Offline Downloader + Geofence Simulator + Audio
 */

(function () {
  'use strict';

  // State store
  const state = {
    currentTab: 'map',
    selectedTriggerType: 'alarm', // 'alarm' or 'notification'
    currentRadius: 200, // meters
    currentLocation: {
      lat: 40.7829, // Central Park, NY
      lng: -73.9654,
      name: 'Central Park Great Lawn'
    },
    reminders: [
      {
        id: 'rem-1',
        title: 'Return Library Books',
        note: 'Drop off philosophy volumes at circulation desk',
        lat: 40.7532,
        lng: -73.9822,
        locationName: 'NY Public Library Main Branch',
        radius: 120,
        triggerType: 'notification',
        enabled: true,
        distance: '450m'
      },
      {
        id: 'rem-2',
        title: 'Pick Up Thyroid Prescription',
        note: 'Check pharmacy counter before 6:00 PM',
        lat: 40.7614,
        lng: -73.9776,
        locationName: '5th Ave Care Pharmacy',
        radius: 180,
        triggerType: 'alarm',
        enabled: true,
        distance: '1.2km'
      },
      {
        id: 'rem-3',
        title: 'Buy Organic Espresso Beans',
        note: 'Ethiopian single origin light roast',
        lat: 40.7359,
        lng: -73.9911,
        locationName: 'Union Square Greenmarket',
        radius: 250,
        triggerType: 'notification',
        enabled: true,
        distance: '3.8km'
      }
    ],
    offlineRegions: [
      {
        id: 'reg-1',
        name: 'Downtown Manhattan (Core)',
        bounds: '40.70 - 40.76 N, -74.02 - -73.96 W',
        zoomLevels: '12 - 16',
        tileCount: 1420,
        sizeMb: 24.8,
        downloadedAt: 'Oct 28, 2026'
      },
      {
        id: 'reg-2',
        name: 'Central Park & Midtown',
        bounds: '40.76 - 40.80 N, -73.98 - -73.94 W',
        zoomLevels: '13 - 16',
        tileCount: 890,
        sizeMb: 15.6,
        downloadedAt: 'Oct 29, 2026'
      }
    ],
    isDownloading: false,
    alarmAudioCtx: null,
    alarmOscillator: null,
    alarmInterval: null
  };

  let map = null;
  let currentMarker = null;
  let radiusCircle = null;
  let reminderMarkers = [];

  // Web Audio Synthesizer for alerts
  function playNotificationChime() {
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      const ctx = new AudioContext();
      
      const osc1 = ctx.createOscillator();
      const osc2 = ctx.createOscillator();
      const gain = ctx.createGain();

      osc1.type = 'sine';
      osc2.type = 'triangle';

      osc1.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
      osc1.frequency.setValueAtTime(880.00, ctx.currentTime + 0.1); // A5

      osc2.frequency.setValueAtTime(587.33, ctx.currentTime);
      osc2.frequency.setValueAtTime(880.00, ctx.currentTime + 0.1);

      gain.gain.setValueAtTime(0.2, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.45);

      osc1.connect(gain);
      osc2.connect(gain);
      gain.connect(ctx.destination);

      osc1.start();
      osc2.start();
      osc1.stop(ctx.currentTime + 0.5);
      osc2.stop(ctx.currentTime + 0.5);
    } catch (e) {
      console.log('Audio not supported or restricted', e);
    }
  }

  function startAlarmSound() {
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      if (!state.alarmAudioCtx) {
        state.alarmAudioCtx = new AudioContext();
      }
      const ctx = state.alarmAudioCtx;
      if (ctx.state === 'suspended') {
        ctx.resume();
      }

      function beep() {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(880, ctx.currentTime);
        osc.frequency.setValueAtTime(1240, ctx.currentTime + 0.12);

        gain.gain.setValueAtTime(0.35, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.28);

        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + 0.3);
      }

      beep();
      if (state.alarmInterval) clearInterval(state.alarmInterval);
      state.alarmInterval = setInterval(beep, 600);
    } catch (e) {
      console.log('Alarm audio playback notice:', e);
    }
  }

  function stopAlarmSound() {
    if (state.alarmInterval) {
      clearInterval(state.alarmInterval);
      state.alarmInterval = null;
    }
  }

  // Initialize Map
  function initMap() {
    if (map) return;
    const mapElement = document.getElementById('map');
    if (!mapElement) return;

    // Standard OpenStreetMap tiles - open source and no API key required
    map = L.map('map', {
      zoomControl: false
    }).setView([state.currentLocation.lat, state.currentLocation.lng], 15);

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(map);

    L.control.zoom({ position: 'bottomright' }).addTo(map);

    // Initial marker & circle
    updateActiveMarkerAndCircle(state.currentLocation.lat, state.currentLocation.lng, state.currentRadius);

    // Map click drops or repositions reminder target
    map.on('click', function (e) {
      state.currentLocation.lat = e.latlng.lat;
      state.currentLocation.lng = e.latlng.lng;
      state.currentLocation.name = 'Pinned Location (' + e.latlng.lat.toFixed(4) + ', ' + e.latlng.lng.toFixed(4) + ')';
      
      updateActiveMarkerAndCircle(e.latlng.lat, e.latlng.lng, state.currentRadius);
      updateLocationInputs();
    });

    renderReminderMapMarkers();
  }

  function updateActiveMarkerAndCircle(lat, lng, radiusMeters) {
    if (currentMarker) {
      currentMarker.setLatLng([lat, lng]);
    } else {
      currentMarker = L.marker([lat, lng], { draggable: true }).addTo(map);
      currentMarker.on('dragend', function (e) {
        const pos = e.target.getLatLng();
        state.currentLocation.lat = pos.lat;
        state.currentLocation.lng = pos.lng;
        state.currentLocation.name = 'Pinned Target (' + pos.lat.toFixed(4) + ', ' + pos.lng.toFixed(4) + ')';
        updateActiveMarkerAndCircle(pos.lat, pos.lng, state.currentRadius);
        updateLocationInputs();
      });
    }

    if (radiusCircle) {
      radiusCircle.setLatLng([lat, lng]);
      radiusCircle.setRadius(radiusMeters);
    } else {
      radiusCircle = L.circle([lat, lng], {
        radius: radiusMeters,
        color: '#1b6c80',
        weight: 2,
        fillColor: '#1b6c80',
        fillOpacity: 0.18
      }).addTo(map);
    }
  }

  function renderReminderMapMarkers() {
    reminderMarkers.forEach(m => map.removeLayer(m));
    reminderMarkers = [];

    state.reminders.forEach(rem => {
      if (!rem.enabled) return;
      const marker = L.circleMarker([rem.lat, rem.lng], {
        radius: 8,
        color: rem.triggerType === 'alarm' ? '#92400e' : '#1b6c80',
        fillColor: rem.triggerType === 'alarm' ? '#fffbeb' : '#e6f1f4',
        fillOpacity: 0.9,
        weight: 2
      }).addTo(map);

      marker.bindPopup('<strong>' + rem.title + '</strong><br>' + rem.locationName + '<br>Radius: ' + rem.radius + 'm (' + rem.triggerType + ')');
      reminderMarkers.push(marker);

      const zone = L.circle([rem.lat, rem.lng], {
        radius: rem.radius,
        color: rem.triggerType === 'alarm' ? '#92400e' : '#1b6c80',
        weight: 1,
        dashArray: '4, 6',
        fillOpacity: 0.05
      }).addTo(map);
      reminderMarkers.push(zone);
    });
  }

  function updateLocationInputs() {
    const latLngBadge = document.getElementById('coords-display');
    if (latLngBadge) {
      latLngBadge.textContent = state.currentLocation.lat.toFixed(4) + ', ' + state.currentLocation.lng.toFixed(4);
    }
    const locInput = document.getElementById('reminder-location-name');
    if (locInput && !locInput.value) {
      locInput.value = state.currentLocation.name;
    }
  }

  // Trigger Type Selection
  function setupTriggerToggles() {
    const alarmBtn = document.getElementById('trigger-opt-alarm');
    const notifBtn = document.getElementById('trigger-opt-notif');

    if (alarmBtn && notifBtn) {
      alarmBtn.addEventListener('click', function () {
        state.selectedTriggerType = 'alarm';
        alarmBtn.classList.add('selected');
        notifBtn.classList.remove('selected');
      });

      notifBtn.addEventListener('click', function () {
        state.selectedTriggerType = 'notification';
        notifBtn.classList.add('selected');
        alarmBtn.classList.remove('selected');
      });
    }
  }

  // Radius Slider
  function setupRadiusSlider() {
    const slider = document.getElementById('radius-slider');
    const display = document.getElementById('radius-display-val');

    if (slider && display) {
      slider.addEventListener('input', function (e) {
        const val = parseInt(e.target.value, 10);
        state.currentRadius = val;
        display.textContent = val + ' meters';
        if (radiusCircle) {
          radiusCircle.setRadius(val);
        }
      });
    }
  }

  // Search places via Nominatim OpenStreetMap
  function setupSearch() {
    const searchInput = document.getElementById('map-search-input');
    const locateBtn = document.getElementById('locate-me-btn');

    if (searchInput) {
      searchInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
          const query = searchInput.value.trim();
          if (!query) return;

          fetch('https://nominatim.openstreetmap.org/search?format=json&q=' + encodeURIComponent(query))
            .then(res => res.json())
            .then(data => {
              if (data && data.length > 0) {
                const first = data[0];
                const lat = parseFloat(first.lat);
                const lon = parseFloat(first.lon);
                state.currentLocation.lat = lat;
                state.currentLocation.lng = lon;
                state.currentLocation.name = first.display_name.split(',')[0];
                
                map.setView([lat, lon], 16);
                updateActiveMarkerAndCircle(lat, lon, state.currentRadius);
                updateLocationInputs();
                const locInput = document.getElementById('reminder-location-name');
                if (locInput) locInput.value = state.currentLocation.name;
              }
            })
            .catch(err => {
              console.log('Search fallback:', err);
            });
        }
      });
    }

    if (locateBtn) {
      locateBtn.addEventListener('click', function () {
        if ('geolocation' in navigator) {
          navigator.geolocation.getCurrentPosition(
            function (pos) {
              const lat = pos.coords.latitude;
              const lon = pos.coords.longitude;
              state.currentLocation.lat = lat;
              state.currentLocation.lng = lon;
              state.currentLocation.name = 'Current Location';
              map.setView([lat, lon], 16);
              updateActiveMarkerAndCircle(lat, lon, state.currentRadius);
              updateLocationInputs();
            },
            function () {
              // fallback to default
              map.setView([state.currentLocation.lat, state.currentLocation.lng], 16);
            }
          );
        }
      });
    }
  }

  // Save Reminder
  function setupSaveReminder() {
    const saveBtn = document.getElementById('save-reminder-btn');
    if (!saveBtn) return;

    saveBtn.addEventListener('click', function () {
      const titleInput = document.getElementById('reminder-title');
      const noteInput = document.getElementById('reminder-note');
      const locInput = document.getElementById('reminder-location-name');

      const title = (titleInput ? titleInput.value.trim() : '') || 'New Georeminder';
      const note = (noteInput ? noteInput.value.trim() : '') || 'Reminder on geofence arrival';
      const locName = (locInput ? locInput.value.trim() : '') || state.currentLocation.name;

      const newReminder = {
        id: 'rem-' + Date.now(),
        title: title,
        note: note,
        lat: state.currentLocation.lat,
        lng: state.currentLocation.lng,
        locationName: locName,
        radius: state.currentRadius,
        triggerType: state.selectedTriggerType,
        enabled: true,
        distance: 'Within range'
      };

      state.reminders.unshift(newReminder);
      renderRemindersList();
      renderReminderMapMarkers();

      // Reset form
      if (titleInput) titleInput.value = '';
      if (noteInput) noteInput.value = '';

      // Switch to Reminders tab
      switchTab('reminders');
    });
  }

  // Render Reminders
  function renderRemindersList() {
    const container = document.getElementById('reminders-list-items');
    if (!container) return;

    container.innerHTML = '';
    state.reminders.forEach(rem => {
      const card = document.createElement('div');
      card.className = 'reminder-card';
      
      const badgeClass = rem.triggerType === 'alarm' ? 'badge-alarm' : 'badge-notification';
      const badgeText = rem.triggerType === 'alarm' ? 'Alarm Alert' : 'Notification';

      card.innerHTML = 
        '<div class="reminder-top-row">' +
          '<div>' +
            '<h4 class="reminder-title">' + escapeHtml(rem.title) + '</h4>' +
            '<div class="reminder-location-name">' + escapeHtml(rem.locationName) + '</div>' +
          '</div>' +
          '<div class="reminder-badge-group">' +
            '<span class="badge-pill ' + badgeClass + '">' + badgeText + '</span>' +
            '<span class="badge-pill badge-radius">' + rem.radius + 'm</span>' +
          '</div>' +
        '</div>' +
        (rem.note ? '<div style="font-size: 13px; color: var(--muted);">' + escapeHtml(rem.note) + '</div>' : '') +
        '<div class="reminder-meta-row">' +
          '<span>Distance: <strong class="num">' + rem.distance + '</strong></span>' +
          '<div class="reminder-actions">' +
            '<button class="action-text-btn simulate-btn" data-id="' + rem.id + '">Simulate Entry</button>' +
            '<button class="action-text-btn delete delete-btn" data-id="' + rem.id + '">Delete</button>' +
          '</div>' +
        '</div>';

      container.appendChild(card);
    });

    // Attach listeners
    container.querySelectorAll('.simulate-btn').forEach(btn => {
      btn.addEventListener('click', function () {
        const id = this.getAttribute('data-id');
        const rem = state.reminders.find(r => r.id === id);
        if (rem) simulateTrigger(rem);
      });
    });

    container.querySelectorAll('.delete-btn').forEach(btn => {
      btn.addEventListener('click', function () {
        const id = this.getAttribute('data-id');
        state.reminders = state.reminders.filter(r => r.id !== id);
        renderRemindersList();
        renderReminderMapMarkers();
      });
    });

    // Update count badges
    const countBadges = document.querySelectorAll('.reminders-count-badge');
    countBadges.forEach(b => b.textContent = state.reminders.length);
  }

  // Offline Tile Download Simulation
  function setupOfflineDownloader() {
    const downloadBtn = document.getElementById('start-download-region-btn');
    const regionNameInput = document.getElementById('offline-region-name');
    const progressBox = document.getElementById('download-progress-container');
    const progressBar = document.getElementById('download-progress-fill');
    const progressPercent = document.getElementById('download-progress-percent');
    const progressTiles = document.getElementById('download-progress-tiles');

    if (!downloadBtn) return;

    downloadBtn.addEventListener('click', function () {
      if (state.isDownloading) return;
      state.isDownloading = true;

      const name = (regionNameInput && regionNameInput.value.trim()) || 'Custom Map Sector';
      progressBox.style.display = 'flex';
      downloadBtn.disabled = true;
      downloadBtn.style.opacity = '0.6';

      let current = 0;
      const totalTiles = 780;
      const interval = setInterval(function () {
        current += Math.floor(Math.random() * 65) + 35;
        if (current >= totalTiles) {
          current = totalTiles;
          clearInterval(interval);
          state.isDownloading = false;
          downloadBtn.disabled = false;
          downloadBtn.style.opacity = '1';

          // Add to offline regions
          state.offlineRegions.unshift({
            id: 'reg-' + Date.now(),
            name: name,
            bounds: 'Active Viewport',
            zoomLevels: '12 - 16',
            tileCount: totalTiles,
            sizeMb: 13.8,
            downloadedAt: 'Just now'
          });

          renderOfflineRegions();

          setTimeout(function () {
            progressBox.style.display = 'none';
            progressBar.style.width = '0%';
            if (regionNameInput) regionNameInput.value = '';
          }, 1200);
        }

        const pct = Math.floor((current / totalTiles) * 100);
        progressBar.style.width = pct + '%';
        progressPercent.textContent = pct + '%';
        progressTiles.textContent = current + ' / ' + totalTiles + ' tiles';
      }, 150);
    });

    renderOfflineRegions();
  }

  function renderOfflineRegions() {
    const list = document.getElementById('offline-regions-list');
    const totalTilesDisplay = document.getElementById('total-offline-tiles-display');
    const totalSizeDisplay = document.getElementById('total-offline-size-display');
    if (!list) return;

    list.innerHTML = '';
    let totalTiles = 0;
    let totalMb = 0;

    state.offlineRegions.forEach(reg => {
      totalTiles += reg.tileCount;
      totalMb += reg.sizeMb;

      const item = document.createElement('div');
      item.className = 'region-item';
      item.innerHTML = 
        '<div>' +
          '<div class="region-name">' + escapeHtml(reg.name) + '</div>' +
          '<div class="region-sub">Zoom ' + reg.zoomLevels + ' &middot; ' + reg.tileCount + ' tiles (' + reg.sizeMb + ' MB)</div>' +
        '</div>' +
        '<button class="action-text-btn delete del-region-btn" data-id="' + reg.id + '">Remove</button>';

      list.appendChild(item);
    });

    list.querySelectorAll('.del-region-btn').forEach(btn => {
      btn.addEventListener('click', function () {
        const id = this.getAttribute('data-id');
        state.offlineRegions = state.offlineRegions.filter(r => r.id !== id);
        renderOfflineRegions();
      });
    });

    if (totalTilesDisplay) totalTilesDisplay.textContent = totalTiles.toLocaleString();
    if (totalSizeDisplay) totalSizeDisplay.textContent = totalMb.toFixed(1) + ' MB';
  }

  // Simulation Trigger: Notification vs Alarm
  function simulateTrigger(reminder) {
    if (reminder.triggerType === 'alarm') {
      triggerFullScreenAlarm(reminder);
    } else {
      triggerHeadsUpNotification(reminder);
    }
  }

  function triggerHeadsUpNotification(reminder) {
    playNotificationChime();

    const toast = document.getElementById('android-notification-toast');
    const title = document.getElementById('notif-title');
    const body = document.getElementById('notif-body');

    if (!toast || !title || !body) return;

    title.textContent = reminder.title;
    body.textContent = 'Entered ' + reminder.radius + 'm zone of ' + reminder.locationName;

    toast.classList.add('show');

    const dismissBtn = document.getElementById('notif-dismiss-btn');
    const openBtn = document.getElementById('notif-open-btn');

    const closeHandler = function () {
      toast.classList.remove('show');
    };

    dismissBtn.onclick = closeHandler;
    openBtn.onclick = function () {
      closeHandler();
      switchTab('reminders');
    };

    setTimeout(closeHandler, 6000);
  }

  function triggerFullScreenAlarm(reminder) {
    startAlarmSound();

    const screen = document.getElementById('android-alarm-screen');
    const heading = document.getElementById('alarm-heading');
    const note = document.getElementById('alarm-note');
    const chip = document.getElementById('alarm-radius-chip');

    if (!screen || !heading || !note) return;

    heading.textContent = reminder.title;
    note.textContent = reminder.note || ('You have entered ' + reminder.locationName);
    chip.textContent = 'Radius ' + reminder.radius + 'm entered \u00b7 High Accuracy GPS';

    screen.classList.add('show');

    const stopBtn = document.getElementById('alarm-stop-btn');
    const snoozeBtn = document.getElementById('alarm-snooze-btn');

    function closeAlarm() {
      stopAlarmSound();
      screen.classList.remove('show');
    }

    stopBtn.onclick = closeAlarm;
    snoozeBtn.onclick = function () {
      closeAlarm();
      setTimeout(function () {
        triggerFullScreenAlarm(reminder);
      }, 5000); // 5 sec demo snooze
    };
  }

  // Tab Navigation
  function switchTab(tabId) {
    state.currentTab = tabId;

    // View contents
    document.querySelectorAll('.screen-view').forEach(view => {
      view.classList.remove('active');
    });
    const targetView = document.getElementById('view-' + tabId);
    if (targetView) targetView.classList.add('active');

    // Bottom tabs
    document.querySelectorAll('.bottom-tab-btn').forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-tab') === tabId);
    });

    // Sidebar buttons
    document.querySelectorAll('.nav-btn[data-tab]').forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-tab') === tabId);
    });

    // Invalidate map size if map tab opened
    if (tabId === 'map' && map) {
      setTimeout(function () {
        map.invalidateSize();
      }, 100);
    }
  }

  function setupNavigation() {
    document.querySelectorAll('.bottom-tab-btn').forEach(btn => {
      btn.addEventListener('click', function () {
        switchTab(this.getAttribute('data-tab'));
      });
    });

    document.querySelectorAll('.nav-btn[data-tab]').forEach(btn => {
      btn.addEventListener('click', function () {
        switchTab(this.getAttribute('data-tab'));
      });
    });

    // View mode selector (Phone vs Expanded)
    const phoneBtn = document.getElementById('mode-phone-btn');
    const expandBtn = document.getElementById('mode-expanded-btn');
    const frame = document.getElementById('phone-frame');

    if (phoneBtn && expandBtn && frame) {
      phoneBtn.addEventListener('click', function () {
        phoneBtn.classList.add('active');
        expandBtn.classList.remove('active');
        frame.classList.remove('mode-expanded');
        if (map) map.invalidateSize();
      });

      expandBtn.addEventListener('click', function () {
        expandBtn.classList.add('active');
        phoneBtn.classList.remove('active');
        frame.classList.add('mode-expanded');
        if (map) map.invalidateSize();
      });
    }

    // Direct simulation buttons from sidebar
    const simAlarmDirect = document.getElementById('simulate-alarm-direct-btn');
    const simNotifDirect = document.getElementById('simulate-notif-direct-btn');

    if (simAlarmDirect) {
      simAlarmDirect.addEventListener('click', function () {
        simulateTrigger(state.reminders[1]); // The pharmacy alarm
      });
    }
    if (simNotifDirect) {
      simNotifDirect.addEventListener('click', function () {
        simulateTrigger(state.reminders[0]); // The library notification
      });
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // Update clock in status bar
  function startClock() {
    const clockEl = document.getElementById('status-bar-clock');
    function update() {
      if (clockEl) {
        const now = new Date();
        const hours = now.getHours();
        const mins = String(now.getMinutes()).padStart(2, '0');
        clockEl.textContent = hours + ':' + mins;
      }
    }
    update();
    setInterval(update, 10000);
  }

  function setupSlidableDrawer() {
    const drawer = document.querySelector('.map-bottom-drawer');
    const handle = document.querySelector('.drawer-drag-handle');
    const titleRow = document.querySelector('.drawer-title-row');
    if (!drawer) return;

    function toggle() {
      drawer.classList.toggle('collapsed');
      setTimeout(function () {
        if (map) map.invalidateSize();
      }, 300);
    }

    if (handle) handle.addEventListener('click', toggle);
    if (titleRow) titleRow.addEventListener('click', toggle);
  }

  // Boot on DOM ready
  document.addEventListener('DOMContentLoaded', function () {
    initMap();
    setupSlidableDrawer();
    setupTriggerToggles();
    setupRadiusSlider();
    setupSearch();
    setupSaveReminder();
    renderRemindersList();
    setupOfflineDownloader();
    setupNavigation();
    startClock();
    updateLocationInputs();
  });
})();
