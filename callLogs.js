import { NativeModules } from 'react-native';

const {CallLogs: NativeCallLogs} = NativeModules;

class CallLogs {
  static async load(limit, filter) {
    if (!filter) {
      return NativeCallLogs.load(limit);
    }
    const {minTimestamp, maxTimestamp, types, phoneNumbers} = filter;
    const phoneNumbersArray = Array.isArray(phoneNumbers) ? 
      phoneNumbers : 
      typeof phoneNumbers === 'string' ? [phoneNumbers] : [];

    const typesArray = Array.isArray(types) ? 
      types.map(x => x.toString()) : 
      (typeof types === 'string' || typeof types === 'object') ? [types.toString()] : [];

    return NativeCallLogs.loadWithFilter(
      limit,
      {
        minTimestamp: minTimestamp ? minTimestamp.toString() : undefined,
        maxTimestamp: maxTimestamp ? maxTimestamp.toString() : undefined,
        types: JSON.stringify(typesArray),
        phoneNumbers: JSON.stringify(phoneNumbersArray),
      }
    );
  }

  static async loadAll() {
    return NativeCallLogs.loadAll();
  }

  static async getLastRowId() {
    return NativeCallLogs.getLastRowId()
  }

  static async getActiveSimCount() {
    return NativeCallLogs.getActiveSimCount()
  }

  static async getTotalDurationOfTheDay(timestamp, callType) {
    return NativeCallLogs.getTotalDurationOfTheDay(String(timestamp), callType)
  }

  static async getTotalDurationDayWise(startDate, endDate, callType) {
    return NativeCallLogs.getTotalDurationDayWise(startDate, endDate, callType)
  }
}

module.exports = CallLogs;
