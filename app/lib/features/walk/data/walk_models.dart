import 'package:freezed_annotation/freezed_annotation.dart';

part 'walk_models.freezed.dart';
part 'walk_models.g.dart';

@freezed
sealed class WalkSpotDto with _$WalkSpotDto {
  const factory WalkSpotDto({
    required double latitude,
    required double longitude,
    required String type, // PEE, POOP, PHOTO, etc.
    required DateTime timestamp,
    String? imageUrl,
    String? note,
  }) = _WalkSpotDto;

  factory WalkSpotDto.fromJson(Map<String, dynamic> json) =>
      _$WalkSpotDtoFromJson(json);
}

@freezed
sealed class WalkResponse with _$WalkResponse {
  const factory WalkResponse({
    required int id,
    required int userId,
    required DateTime startTime,
    required DateTime endTime,
    required double distanceKm,
    required int durationSeconds,
    required List<List<double>> path,
    required List<WalkSpotDto> spots,
    double? caloriesBurned,
    String? notes,
    String? startAddress,
    double? startLatitude,
    double? startLongitude,
    double? endLatitude,
    double? endLongitude,
    @Default([]) List<String> petNames,
    @Default([]) List<int> petIds,
  }) = _WalkResponse;

  factory WalkResponse.fromJson(Map<String, dynamic> json) =>
      _$WalkResponseFromJson(json);
}

@freezed
sealed class CreateWalkRequest with _$CreateWalkRequest {
  const factory CreateWalkRequest({
    required DateTime startTime,
    required DateTime endTime,
    required double distanceKm,
    required int durationSeconds,
    required List<List<double>> path, // [[lat, lon], [lat, lon]]
    @Default([]) List<WalkSpotDto> spots,
    double? caloriesBurned,
    String? notes,
    @Default([]) List<int> petIds,
    @Default(true) bool isPublic,
    int? followedCourseId,
  }) = _CreateWalkRequest;

  factory CreateWalkRequest.fromJson(Map<String, dynamic> json) =>
      _$CreateWalkRequestFromJson(json);
}
