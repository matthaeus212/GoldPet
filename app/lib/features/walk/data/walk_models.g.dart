// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'walk_models.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$WalkSpotDtoImpl _$$WalkSpotDtoImplFromJson(Map<String, dynamic> json) =>
    _$WalkSpotDtoImpl(
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      type: json['type'] as String,
      timestamp: DateTime.parse(json['timestamp'] as String),
      imageUrl: json['imageUrl'] as String?,
      note: json['note'] as String?,
    );

Map<String, dynamic> _$$WalkSpotDtoImplToJson(_$WalkSpotDtoImpl instance) =>
    <String, dynamic>{
      'latitude': instance.latitude,
      'longitude': instance.longitude,
      'type': instance.type,
      'timestamp': instance.timestamp.toIso8601String(),
      'imageUrl': instance.imageUrl,
      'note': instance.note,
    };

_$WalkResponseImpl _$$WalkResponseImplFromJson(Map<String, dynamic> json) =>
    _$WalkResponseImpl(
      id: (json['id'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      startTime: DateTime.parse(json['startTime'] as String),
      endTime: DateTime.parse(json['endTime'] as String),
      distanceKm: (json['distanceKm'] as num).toDouble(),
      durationSeconds: (json['durationSeconds'] as num).toInt(),
      path: (json['path'] as List<dynamic>)
          .map(
            (e) =>
                (e as List<dynamic>).map((e) => (e as num).toDouble()).toList(),
          )
          .toList(),
      spots: (json['spots'] as List<dynamic>)
          .map((e) => WalkSpotDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      caloriesBurned: (json['caloriesBurned'] as num?)?.toDouble(),
      notes: json['notes'] as String?,
      startAddress: json['startAddress'] as String?,
      startLatitude: (json['startLatitude'] as num?)?.toDouble(),
      startLongitude: (json['startLongitude'] as num?)?.toDouble(),
      endLatitude: (json['endLatitude'] as num?)?.toDouble(),
      endLongitude: (json['endLongitude'] as num?)?.toDouble(),
      petNames:
          (json['petNames'] as List<dynamic>?)
              ?.map((e) => e as String)
              .toList() ??
          const [],
      petIds:
          (json['petIds'] as List<dynamic>?)
              ?.map((e) => (e as num).toInt())
              .toList() ??
          const [],
    );

Map<String, dynamic> _$$WalkResponseImplToJson(_$WalkResponseImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'userId': instance.userId,
      'startTime': instance.startTime.toIso8601String(),
      'endTime': instance.endTime.toIso8601String(),
      'distanceKm': instance.distanceKm,
      'durationSeconds': instance.durationSeconds,
      'path': instance.path,
      'spots': instance.spots,
      'caloriesBurned': instance.caloriesBurned,
      'notes': instance.notes,
      'startAddress': instance.startAddress,
      'startLatitude': instance.startLatitude,
      'startLongitude': instance.startLongitude,
      'endLatitude': instance.endLatitude,
      'endLongitude': instance.endLongitude,
      'petNames': instance.petNames,
      'petIds': instance.petIds,
    };

_$CreateWalkRequestImpl _$$CreateWalkRequestImplFromJson(
  Map<String, dynamic> json,
) => _$CreateWalkRequestImpl(
  startTime: DateTime.parse(json['startTime'] as String),
  endTime: DateTime.parse(json['endTime'] as String),
  distanceKm: (json['distanceKm'] as num).toDouble(),
  durationSeconds: (json['durationSeconds'] as num).toInt(),
  path: (json['path'] as List<dynamic>)
      .map(
        (e) => (e as List<dynamic>).map((e) => (e as num).toDouble()).toList(),
      )
      .toList(),
  spots:
      (json['spots'] as List<dynamic>?)
          ?.map((e) => WalkSpotDto.fromJson(e as Map<String, dynamic>))
          .toList() ??
      const [],
  caloriesBurned: (json['caloriesBurned'] as num?)?.toDouble(),
  notes: json['notes'] as String?,
  petIds:
      (json['petIds'] as List<dynamic>?)
          ?.map((e) => (e as num).toInt())
          .toList() ??
      const [],
  isPublic: json['isPublic'] as bool? ?? true,
  followedCourseId: (json['followedCourseId'] as num?)?.toInt(),
);

Map<String, dynamic> _$$CreateWalkRequestImplToJson(
  _$CreateWalkRequestImpl instance,
) => <String, dynamic>{
  'startTime': instance.startTime.toIso8601String(),
  'endTime': instance.endTime.toIso8601String(),
  'distanceKm': instance.distanceKm,
  'durationSeconds': instance.durationSeconds,
  'path': instance.path,
  'spots': instance.spots,
  'caloriesBurned': instance.caloriesBurned,
  'notes': instance.notes,
  'petIds': instance.petIds,
  'isPublic': instance.isPublic,
  'followedCourseId': instance.followedCourseId,
};
