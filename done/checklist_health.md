# Migrate Health Data Checklist
- [ ] Search for current Fitbit API usage.
- [ ] Search for current health data retrieval methods.
- [ ] Update `build.gradle` to include `health-services-client`.
- [ ] Create or update a Kotlin service to use `HealthServices.getClient(context).passiveMonitoringClient`.
- [ ] Migrate steps and heart rate to the new passive monitoring client.
- [ ] Ensure `AndroidManifest.xml` has the right permissions (e.g. `BODY_SENSORS`, `ACTIVITY_RECOGNITION`).
- [ ] Update documentation/plan.
- [ ] Move checklists and plans to `done/`.
